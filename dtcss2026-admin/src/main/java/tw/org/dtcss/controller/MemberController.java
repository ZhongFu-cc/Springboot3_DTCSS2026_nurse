package tw.org.dtcss.controller;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wf.captcha.SpecCaptcha;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.SaTokenInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import tw.org.dtcss.convert.MemberConvert;
import tw.org.dtcss.exception.RegistrationInfoException;
import tw.org.dtcss.manager.MemberAuthManager;
import tw.org.dtcss.manager.MemberManager;
import tw.org.dtcss.manager.MemberOrderManager;
import tw.org.dtcss.manager.MemberRegistrationManager;
import tw.org.dtcss.manager.MemberTagManager;
import tw.org.dtcss.pojo.DTO.AddMemberForAdminDTO;
import tw.org.dtcss.pojo.DTO.ForgetPwdDTO;
import tw.org.dtcss.pojo.DTO.GroupRegistrationDTO;
import tw.org.dtcss.pojo.DTO.MemberEmailLogin;
import tw.org.dtcss.pojo.DTO.MemberIdCardLogin;
import tw.org.dtcss.pojo.DTO.MemberLoginDTO;
import tw.org.dtcss.pojo.DTO.PutMemberIdDTO;
import tw.org.dtcss.pojo.DTO.addEntityDTO.AddMemberDTO;
import tw.org.dtcss.pojo.DTO.addEntityDTO.AddTagToMemberDTO;
import tw.org.dtcss.pojo.DTO.putEntityDTO.PutMemberDTO;
import tw.org.dtcss.pojo.DTO.putEntityDTO.PutMemberForAdminDTO;
import tw.org.dtcss.pojo.VO.MemberOrderVO;
import tw.org.dtcss.pojo.VO.MemberTagVO;
import tw.org.dtcss.pojo.VO.MemberVO;
import tw.org.dtcss.pojo.entity.Member;
import tw.org.dtcss.pojo.entity.Orders;
import tw.org.dtcss.saToken.StpKit;
import tw.org.dtcss.service.MemberService;
import tw.org.dtcss.utils.R;

@Tag(name = "會員API")
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/member")
public class MemberController {

	@Qualifier("businessRedissonClient")
	private final RedissonClient redissonClient;

	private final MemberService memberService;
	private final MemberConvert memberConvert;
	private final MemberOrderManager memberOrderManager;
	private final MemberRegistrationManager memberRegistrationManager;
	private final MemberAuthManager memberAuthManager;
	private final MemberTagManager memberTagManager;
	private final MemberManager memberManager;

	@GetMapping("/captcha")
	@Operation(summary = "獲取驗證碼")
	public R<HashMap<Object, Object>> captcha() {
		SpecCaptcha specCaptcha = new SpecCaptcha(130, 50, 5);
		String verCode = specCaptcha.text().toLowerCase();
		String key = "Captcha:" + UUID.randomUUID().toString();
		// 明確調用String類型的Bucket,存入String類型的Value 進redis並設置過期時間為30分鐘
		redissonClient.<String>getBucket(key).set(verCode, 30, TimeUnit.MINUTES);

		// 将key和base64返回给前端
		HashMap<Object, Object> hashMap = new HashMap<>();
		hashMap.put("key", key);
		hashMap.put("image", specCaptcha.toBase64());

		return R.ok(hashMap);
	}

	@GetMapping("owner")
	@Operation(summary = "查詢單一會員For會員本人")
	@Parameters({
			@Parameter(name = "Authorization-member", description = "請求頭token,token-value開頭必須為Bearer ", required = true, in = ParameterIn.HEADER) })
	@SaCheckLogin(type = StpKit.MEMBER_TYPE)
	public R<MemberVO> getMemberForOwner() {
		// 根據token 拿取本人的數據
		Member memberCache = memberService.getMemberInfo();
		MemberVO memberVO = memberOrderManager.getMemberVO(memberCache.getMemberId());
		return R.ok(memberVO);
	}

	@GetMapping("{id}")
	@Operation(summary = "查詢單一會員For管理者")
	@SaCheckRole("super-admin")
	public R<Member> getMember(@PathVariable("id") Long memberId) {
		Member member = memberService.getMember(memberId);
		return R.ok(member);
	}

	@GetMapping
	@Operation(summary = "查詢全部會員")
	@Parameters({
			@Parameter(name = "Authorization", description = "請求頭token,token-value開頭必須為Bearer ", required = true, in = ParameterIn.HEADER) })
	@SaCheckRole("super-admin")
	public R<List<MemberVO>> getMemberList() {
		List<Member> memberList = memberService.getMemberList();
		List<MemberVO> memberVOList = memberConvert.entityListToVOList(memberList);
		return R.ok(memberVOList);
	}

	@GetMapping("pagination")
	@Operation(summary = "查詢全部會員(分頁)")
	@Parameters({
			@Parameter(name = "Authorization", description = "請求頭token,token-value開頭必須為Bearer ", required = true, in = ParameterIn.HEADER) })
	@SaCheckRole("super-admin")
	public R<IPage<Member>> getMemberPage(@RequestParam Integer page, @RequestParam Integer size) {
		Page<Member> pageable = new Page<Member>(page, size);
		IPage<Member> memberPage = memberService.getMemberPage(pageable);
		return R.ok(memberPage);
	}

	@GetMapping("count")
	@Operation(summary = "查詢全部會員總數")
	@Parameters({
			@Parameter(name = "Authorization", description = "請求頭token,token-value開頭必須為Bearer ", required = true, in = ParameterIn.HEADER) })
	@SaCheckRole("super-admin")
	public R<Long> getMemberCount() {
		Long memberCount = memberService.getMemberCount();
		return R.ok(memberCount);
	}

	@GetMapping("count-by-order-status")
	@SaCheckRole("super-admin")
	@Parameters({
			@Parameter(name = "Authorization", description = "請求頭token,token-value開頭必須為Bearer ", required = true, in = ParameterIn.HEADER) })
	@Operation(summary = "根據訂單繳費狀態,查詢相符的會員總數")
	public R<Integer> getMemberCountByStatus(Integer status) {
		Integer memberCount = memberOrderManager.getMemberOrderCount(status);
		return R.ok(memberCount);
	}

	// 暫時沒用到,因為沒有註冊費之外的訂單
	@GetMapping("member-and-order")
	@SaCheckRole("super-admin")
	@Parameters({
			@Parameter(name = "Authorization", description = "請求頭token,token-value開頭必須為Bearer ", required = true, in = ParameterIn.HEADER) })
	@Operation(summary = "根據訂單繳費狀態,查詢相符的會員列表")
	public R<IPage<MemberOrderVO>> getMemberOrder(@RequestParam Integer page, @RequestParam Integer size,
			@RequestParam(value = "status", required = false) Integer status,
			@RequestParam(value = "queryText", required = false) String queryText) {
		Page<Orders> pageable = new Page<Orders>(page, size);
		IPage<MemberOrderVO> memberOrderVO = memberOrderManager.getMemberOrderVO(pageable, status, queryText);

		return R.ok(memberOrderVO);
	}

	@GetMapping("unpaid-member")
	@SaCheckRole("super-admin")
	@Parameters({
			@Parameter(name = "Authorization", description = "請求頭token,token-value開頭必須為Bearer ", required = true, in = ParameterIn.HEADER) })
	@Operation(summary = "根據條件,查詢註冊費未付款的會員列表")
	public R<IPage<MemberTagVO>> getUnpaidMember(@RequestParam Integer page, @RequestParam Integer size,
			@RequestParam(value = "country", required = true) String country,
			@RequestParam(value = "queryText", required = false) String queryText) {
		Page<Member> pageable = new Page<Member>(page, size);
		IPage<MemberTagVO> unpaidMemberPage = memberOrderManager.getUnpaidMemberPage(pageable, country, queryText);

		return R.ok(unpaidMemberPage);
	}

	@PostMapping
	@Operation(summary = "新增單一會員，也就是註冊功能")
	public R<SaTokenInfo> saveMember(@RequestBody @Valid AddMemberDTO addMemberDTO) throws RegistrationInfoException {
		// 透過key 獲取redis中的驗證碼
		String redisCode = redissonClient.<String>getBucket(addMemberDTO.getVerificationKey()).get();
		String userVerificationCode = addMemberDTO.getVerificationCode();

		// 判斷驗證碼是否正確,如果不正確就直接返回前端,不做後續的業務處理
		if (userVerificationCode == null || redisCode == null
				|| !redisCode.equals(userVerificationCode.trim().toLowerCase())) {
			return R.fail("Verification code is incorrect");
		}

		// 驗證通過,刪除key 並往後執行添加操作
		redissonClient.getBucket(addMemberDTO.getVerificationKey()).delete();

		SaTokenInfo tokenInfo = memberRegistrationManager.addMember(addMemberDTO);

		return R.ok(tokenInfo);
	}

	@PostMapping("admin")
	@Operation(summary = "新增單一會員For管理者")
	@SaCheckRole("super-admin")
	@Parameters({
			@Parameter(name = "Authorization", description = "請求頭token,token-value開頭必須為Bearer ", required = true, in = ParameterIn.HEADER) })
	public R<Void> saveMemberForAdmin(@RequestBody @Valid AddMemberForAdminDTO addMemberForAdminDTO) {
		memberRegistrationManager.addMemberForAdmin(addMemberForAdminDTO);

		return R.ok();
	}

	@PostMapping("group")
	@Operation(summary = "團體報名，也就是團體註冊功能")
	public R<Void> groupRegistration(@RequestBody @Valid GroupRegistrationDTO groupRegistrationDTO)
			throws RegistrationInfoException {
		// 透過key 獲取redis中的驗證碼
		String redisCode = redissonClient.<String>getBucket(groupRegistrationDTO.getVerificationKey()).get();
		String userVerificationCode = groupRegistrationDTO.getVerificationCode();

		// 判斷驗證碼是否正確,如果不正確就直接返回前端,不做後續的業務處理
		if (userVerificationCode == null || redisCode == null
				|| !redisCode.equals(userVerificationCode.trim().toLowerCase())) {
			return R.fail("Verification code is incorrect");
		}

		// 驗證通過,刪除key 並往後執行添加操作
		redissonClient.getBucket(groupRegistrationDTO.getVerificationKey()).delete();
		memberRegistrationManager.addGroupMember(groupRegistrationDTO);

		return R.ok();
	}

	/**
	 * 個人修改自身資料
	 * 
	 * @param putMemberDTO
	 * @return
	 */
	@PutMapping("owner")
	@Parameters({
			@Parameter(name = "Authorization-member", description = "請求頭token,token-value開頭必須為Bearer ", required = true, in = ParameterIn.HEADER) })
	@SaCheckLogin(type = StpKit.MEMBER_TYPE)
	@Operation(summary = "修改會員資料For會員本人")
	public R<Member> updateMemberForOwner(@RequestBody @Valid PutMemberDTO putMemberDTO) {
		// 根據token 拿取本人的數據
		Member memberCache = memberService.getMemberInfo();
		if (memberCache.getMemberId().equals(putMemberDTO.getMemberId())) {
			memberService.updateMember(putMemberDTO);
			return R.ok();
		}

		return R.fail("The Token is not the user's own and cannot retrieve non-user's information.");

	}

	/**
	 * 管理者修改的必填項為 title、firstName、lastName、country、category<br>
	 * 其餘都可以不用填
	 * 
	 * @param putMemberForAdminDTO
	 * @return
	 */
	@PutMapping
	@Parameters({
			@Parameter(name = "Authorization", description = "請求頭token,token-value開頭必須為Bearer ", required = true, in = ParameterIn.HEADER) })
	@Operation(summary = "修改會員資料For管理者")
	@SaCheckRole("super-admin")
	public R<Member> updateMember(@RequestBody @Valid PutMemberForAdminDTO putMemberForAdminDTO) {
		// 直接更新會員
		memberService.updateMemberForAdmin(putMemberForAdminDTO);
		return R.ok();

	}

	@PutMapping("unpaid-member")
	@SaCheckRole("super-admin")
	@Parameters({
			@Parameter(name = "Authorization", description = "請求頭token,token-value開頭必須為Bearer ", required = true, in = ParameterIn.HEADER) })
	@Operation(summary = "更新註冊費未付款的會員，狀態改為已付款")
	public R<Void> updateUnpaidMember(@RequestBody @Valid PutMemberIdDTO putMemberIdDTO) {
		memberOrderManager.approveUnpaidMember(putMemberIdDTO.getMemberId());
		return R.ok();
	}

	@DeleteMapping("{id}")
	@Parameters({
			@Parameter(name = "Authorization", description = "請求頭token,token-value開頭必須為Bearer ", required = true, in = ParameterIn.HEADER) })
	@SaCheckRole("super-admin")
	@Operation(summary = "刪除會員")
	public R<Member> deleteMember(@PathVariable("id") Long memberId) {
		memberManager.deleteMember(memberId);
		return R.ok();
	}

	@DeleteMapping
	@Operation(summary = "批量刪除會員")
	@Parameters({
			@Parameter(name = "Authorization", description = "請求頭token,token-value開頭必須為Bearer ", required = true, in = ParameterIn.HEADER) })
	@SaCheckRole("super-admin")
	public R<Void> batchDeleteMember(@RequestBody List<Long> ids) {
		memberManager.deleteMemberList(ids);
		return R.ok();

	}

	/** 以下與會員登入有關 */

	/**
	 * 僅靠 email 登入
	 */
	public record OnlyEmailLogin(@NotBlank @Schema(description = "E-Mail") String email,

			@NotBlank @Schema(description = "驗證碼key") String verificationKey,

			@NotBlank @Schema(description = "用戶輸入的驗證碼") String verificationCode) {
	}

	@Operation(summary = "會員登入-僅使用 email ")
	@PostMapping("login-only-email")
	public R<SaTokenInfo> login(@Valid @RequestBody OnlyEmailLogin onlyEmailLogin) {

		// 透過key 獲取redis中的驗證碼
		String redisCode = redissonClient.<String>getBucket(onlyEmailLogin.verificationKey()).get();
		String userVerificationCode = onlyEmailLogin.verificationCode();

		// 判斷驗證碼是否正確,如果不正確就直接返回前端,不做後續的業務處理
		if (userVerificationCode == null || redisCode == null
				|| !redisCode.equals(userVerificationCode.trim().toLowerCase())) {
			return R.fail("Verification code is incorrect");
		}

		// 驗證通過,刪除key 並往後執行添加操作
		redissonClient.getBucket(onlyEmailLogin.verificationKey()).delete();
		SaTokenInfo tokenInfo = memberAuthManager.onlyEmailLogin(onlyEmailLogin.email());
		return R.ok(tokenInfo);
	}

	@Operation(summary = "會員登入-使用 email 和 password")
	@PostMapping("login")
	public R<SaTokenInfo> login(@Valid @RequestBody MemberEmailLogin memberEmailLogin) {

		// 透過key 獲取redis中的驗證碼
		String redisCode = redissonClient.<String>getBucket(memberEmailLogin.getVerificationKey()).get();
		String userVerificationCode = memberEmailLogin.getVerificationCode();

		// 判斷驗證碼是否正確,如果不正確就直接返回前端,不做後續的業務處理
		if (userVerificationCode == null || redisCode == null
				|| !redisCode.equals(userVerificationCode.trim().toLowerCase())) {
			return R.fail("Verification code is incorrect");
		}

		// 驗證通過,刪除key 並往後執行添加操作
		redissonClient.getBucket(memberEmailLogin.getVerificationKey()).delete();
		SaTokenInfo tokenInfo = memberAuthManager.login(memberEmailLogin);
		return R.ok(tokenInfo);
	}

	@Operation(summary = "會員登入-使用 id_card 和 password")
	@PostMapping("login-idCard")
	public R<SaTokenInfo> login(@Valid @RequestBody MemberIdCardLogin memberIdCardLogin) {

		// 透過key 獲取redis中的驗證碼
		String redisCode = redissonClient.<String>getBucket(memberIdCardLogin.getVerificationKey()).get();
		String userVerificationCode = memberIdCardLogin.getVerificationCode();

		// 判斷驗證碼是否正確,如果不正確就直接返回前端,不做後續的業務處理
		if (userVerificationCode == null || redisCode == null
				|| !redisCode.equals(userVerificationCode.trim().toLowerCase())) {
			return R.fail("Verification code is incorrect");
		}

		// 驗證通過,刪除key 並往後執行添加操作
		redissonClient.getBucket(memberIdCardLogin.getVerificationKey()).delete();
		SaTokenInfo tokenInfo = memberAuthManager.login(memberIdCardLogin);
		return R.ok(tokenInfo);
	}

	@Operation(summary = "「外國」會員登入 - 使用 email 和 password，國籍綁定為「非」台灣")
	@PostMapping("login-foreign")
	public R<SaTokenInfo> loginForForeign(@Valid @RequestBody MemberLoginDTO memberLoginDTO) {

		// 透過key 獲取redis中的驗證碼
		String redisCode = redissonClient.<String>getBucket(memberLoginDTO.getVerificationKey()).get();
		String userVerificationCode = memberLoginDTO.getVerificationCode();

		// 判斷驗證碼是否正確,如果不正確就直接返回前端,不做後續的業務處理
		if (userVerificationCode == null || redisCode == null
				|| !redisCode.equals(userVerificationCode.trim().toLowerCase())) {
			return R.fail("Verification code is incorrect");
		}

		// 驗證通過,刪除key 並往後執行添加操作
		redissonClient.getBucket(memberLoginDTO.getVerificationKey()).delete();
		SaTokenInfo tokenInfo = memberAuthManager.foreignLogin(memberLoginDTO);
		return R.ok(tokenInfo);
	}

	@Operation(summary = "「國內」會員登入 - 使用 id_card 和 password，國籍綁定為 台灣")
	@PostMapping("login-local")
	public R<SaTokenInfo> loginForLocal(@Valid @RequestBody MemberLoginDTO memberLoginDTO) {

		// 透過key 獲取redis中的驗證碼
		String redisCode = redissonClient.<String>getBucket(memberLoginDTO.getVerificationKey()).get();
		String userVerificationCode = memberLoginDTO.getVerificationCode();

		// 判斷驗證碼是否正確,如果不正確就直接返回前端,不做後續的業務處理
		if (userVerificationCode == null || redisCode == null
				|| !redisCode.equals(userVerificationCode.trim().toLowerCase())) {
			return R.fail("Verification code is incorrect");
		}

		// 驗證通過,刪除key 並往後執行添加操作
		redissonClient.getBucket(memberLoginDTO.getVerificationKey()).delete();
		SaTokenInfo tokenInfo = memberAuthManager.localLogin(memberLoginDTO);
		return R.ok(tokenInfo);
	}

	@Operation(summary = "會員登出")
	@Parameters({
			@Parameter(name = "Authorization-member", description = "請求頭token,token-value開頭必須為Bearer ", required = true, in = ParameterIn.HEADER) })
	@SaCheckLogin(type = StpKit.MEMBER_TYPE)
	@PostMapping("logout")
	public R<Void> logout() {
		memberAuthManager.logout();
		return R.ok();
	}

	@Operation(summary = "找回密碼")
	@PostMapping("forget-password")
	public R<Void> forgetPassword(@Validated @RequestBody ForgetPwdDTO forgetPwdDTO) throws MessagingException {
		memberAuthManager.forgetPassword(forgetPwdDTO.getEmail());
		return R.ok("A password retrieval email has been sent to your mailbox");
	}

	@Operation(summary = "獲取緩存內的會員資訊")
	@SaCheckLogin(type = StpKit.MEMBER_TYPE)
	@Parameters({
			@Parameter(name = "Authorization-member", description = "請求頭token,token-value開頭必須為Bearer ", required = true, in = ParameterIn.HEADER), })
	@GetMapping("getMemberInfo")
	public R<Member> getMemberInfo() {

		// 獲取token 對應會員資料
		Member memberInfo = memberAuthManager.getMemberInfo();

		// 返回會員資料
		return R.ok(memberInfo);

	}

	/**
	 * 產生參加證明，測試時不能使用 knife4j 或者 swagger
	 * 
	 * @param response
	 * @throws IOException
	 */
	@Operation(summary = "產生參加證明")
	@SaCheckLogin(type = StpKit.MEMBER_TYPE)
	@Parameters({
			@Parameter(name = "Authorization-member", description = "請求頭token,token-value開頭必須為Bearer ", required = true, in = ParameterIn.HEADER), })
	@GetMapping("certificate")
	public void generateCertificate(HttpServletResponse response) throws IOException {

		// 1.獲取token 對應會員資料
		Member memberInfo = memberAuthManager.getMemberInfo();

		// 2.將會員ID 及 響應Servlet給Manager 由它創建參加證明
		memberManager.generateCertificate(response, memberInfo.getMemberId());

	}

	@Operation(summary = "產生 Invoice")
	@SaCheckLogin(type = StpKit.MEMBER_TYPE)
	@Parameters({
			@Parameter(name = "Authorization-member", description = "請求頭token,token-value開頭必須為Bearer ", required = true, in = ParameterIn.HEADER), })
	@GetMapping("conference_invoice")
	public void generateConferenceInvoice(HttpServletResponse response) throws IOException {

		// 1.獲取token 對應會員資料
		Member memberInfo = memberAuthManager.getMemberInfo();

		// 2.將會員ID 及 響應Servlet給Manager 由它創建參加證明
		memberManager.generateConferenceInvoice(response, memberInfo.getMemberId());

	}

	/** 以下是跟Tag有關的Controller */

	@Operation(summary = "根據會員ID 查詢會員資料及他持有的標籤")
	@Parameters({
			@Parameter(name = "Authorization", description = "請求頭token,token-value開頭必須為Bearer ", required = true, in = ParameterIn.HEADER) })
	@SaCheckRole("super-admin")
	@GetMapping("tag/{id}")
	public R<MemberTagVO> getMemberTagVOByMember(@PathVariable("id") Long memberId) {
		MemberTagVO memberTagVOByMember = memberTagManager.getMemberTagVOByMember(memberId);
		return R.ok(memberTagVOByMember);

	}

	@Operation(summary = "根據條件 查詢會員資料及他持有的標籤(分頁)")
	@SaCheckRole("super-admin")
	@Parameters({
			@Parameter(name = "Authorization", description = "請求頭token,token-value開頭必須為Bearer ", required = true, in = ParameterIn.HEADER) })
	@GetMapping("tag/pagination")
	public R<IPage<MemberTagVO>> getMemberTagVOsByQuery(@RequestParam Integer page, @RequestParam Integer size,
			@RequestParam(required = false) String queryText, @RequestParam(required = false) Integer status) {

		Page<Member> pageInfo = new Page<>(page, size);
		IPage<MemberTagVO> memberList;

		memberList = memberTagManager.getMemberTagVOByQuery(pageInfo, queryText, status);

		return R.ok(memberList);
	}

	@Operation(summary = "為會員新增/更新/刪除 複數標籤")
	@Parameters({
			@Parameter(name = "Authorization", description = "請求頭token,token-value開頭必須為Bearer ", required = true, in = ParameterIn.HEADER) })
	@SaCheckRole("super-admin")
	@PutMapping("tag")
	public R<Void> assignTagToMember(@Validated @RequestBody AddTagToMemberDTO addTagToMemberDTO) {
		memberTagManager.assignTagToMember(addTagToMemberDTO.getTargetTagIdList(), addTagToMemberDTO.getMemberId());
		return R.ok();

	}

	@Operation(summary = "下載會員excel列表")
	@SaCheckRole("super-admin")
	@Parameters({
			@Parameter(name = "Authorization", description = "請求頭token,token-value開頭必須為Bearer ", required = true, in = ParameterIn.HEADER) })
	@GetMapping("/download-excel")
	public void downloadExcel(HttpServletResponse response) throws IOException {
		memberOrderManager.downloadExcel(response);
	}

}
