package tw.org.dtcss.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.SaTokenInfo;
import lombok.RequiredArgsConstructor;
import tw.org.dtcss.constants.I18nMessageKey;
import tw.org.dtcss.convert.MemberConvert;
import tw.org.dtcss.enums.OrderStatusEnum;
import tw.org.dtcss.exception.AccountPasswordWrongException;
import tw.org.dtcss.exception.ForgetPasswordException;
import tw.org.dtcss.exception.MemberException;
import tw.org.dtcss.exception.RegisteredAlreadyExistsException;
import tw.org.dtcss.helper.MessageHelper;
import tw.org.dtcss.mapper.MemberMapper;
import tw.org.dtcss.pojo.DTO.AddGroupMemberDTO;
import tw.org.dtcss.pojo.DTO.AddMemberForAdminDTO;
import tw.org.dtcss.pojo.DTO.MemberEmailLogin;
import tw.org.dtcss.pojo.DTO.MemberIdCardLogin;
import tw.org.dtcss.pojo.DTO.MemberLoginDTO;
import tw.org.dtcss.pojo.DTO.WalkInRegistrationDTO;
import tw.org.dtcss.pojo.DTO.addEntityDTO.AddMemberDTO;
import tw.org.dtcss.pojo.DTO.putEntityDTO.PutMemberDTO;
import tw.org.dtcss.pojo.DTO.putEntityDTO.PutMemberForAdminDTO;
import tw.org.dtcss.pojo.VO.MemberOrderVO;
import tw.org.dtcss.pojo.VO.MemberTagVO;
import tw.org.dtcss.pojo.entity.Attendees;
import tw.org.dtcss.pojo.entity.Member;
import tw.org.dtcss.pojo.entity.Orders;
import tw.org.dtcss.saToken.StpKit;
import tw.org.dtcss.service.MemberService;
import tw.org.dtcss.utils.CountryUtil;

@Service
@RequiredArgsConstructor
public class MemberServiceImpl extends ServiceImpl<MemberMapper, Member> implements MemberService {

	private static final String MEMBER_CACHE_INFO_KEY = "memberInfo";
	private final MessageHelper messageHelper;
	private final MemberConvert memberConvert;

	@Override
	public Member getMember(Long memberId) {
		return baseMapper.selectById(memberId);
	}

	@Override
	public String getOnlyMemberName(Member member) {
		String chineseName = member.getChineseName();
		if (StringUtils.isNotBlank(chineseName)) {
			return chineseName;
		}

		String firstName = Optional.ofNullable(member.getFirstName()).orElse("");
		String lastName = Optional.ofNullable(member.getLastName()).orElse("");
		return (firstName + " " + lastName).trim();
	}

	@Override
	public List<Member> getMembersEfficiently() {
		return baseMapper.selectMembers();
	}

	@Override
	public List<Member> getMemberList() {
		List<Member> memberList = baseMapper.selectList(null);
		return memberList;
	}

	@Override
	public List<Member> getMemberListByIds(Collection<Long> memberIds) {

		// 1.如果memberIds為空,返回空數組
		if (memberIds.isEmpty()) {
			return Collections.emptyList();
		}

		// 2.如果有則直接查詢
		return baseMapper.selectBatchIds(memberIds);

	}

	@Override
	public List<Member> getMembersByQuery(String queryText) {
		LambdaQueryWrapper<Member> memberWrapper = new LambdaQueryWrapper<>();

		// 當 queryText 不為空字串、空格字串、Null 時才加入篩選條件
		memberWrapper.and(StringUtils.isNotBlank(queryText),
				wrapper -> wrapper.like(Member::getChineseName, queryText)
						.or()
						.like(Member::getFirstName, queryText)
						.or()
						.like(Member::getLastName, queryText)
						.or()
						.like(Member::getPhone, queryText)
						.or()
						.like(Member::getIdCard, queryText)
						.or()
						.like(Member::getEmail, queryText));

		return baseMapper.selectList(memberWrapper);

	}

	@Override
	public IPage<Member> getMemberPage(Page<Member> page) {
		return baseMapper.selectPage(page, null);
	}

	@Override
	public Long getMemberCount() {
		return baseMapper.selectCount(null);
	}

	@Override
	public Integer getMemberOrderCount(List<Orders> orderList) {
		// 從訂單中抽出memberId,變成List
		List<Long> memberIdList = orderList.stream().map(Orders::getMemberId).collect(Collectors.toList());

		// 返回代表符合繳費狀態的註冊費訂單的會員人數
		return memberIdList.size();
	}

	@Override
	public Member getMemberByEmail(String email) {
		// 1.透過Email查詢Member
		LambdaQueryWrapper<Member> memberQueryWrapper = new LambdaQueryWrapper<>();
		memberQueryWrapper.eq(Member::getEmail, email);

		Member member = baseMapper.selectOne(memberQueryWrapper);
		// 2.如果沒找到該email的member，則直接丟異常給全局處理
		if (member == null) {
			throw new ForgetPasswordException(messageHelper.get(I18nMessageKey.Registration.Auth.EMAIL_NOT_FOUND));
		}

		return member;
	}

	@Override
	public List<Member> getMembersByGroupCodeAndRole(String groupCode, String groupRole) {
		LambdaQueryWrapper<Member> memberQueryWrapper = new LambdaQueryWrapper<>();
		memberQueryWrapper.eq(Member::getGroupCode, groupCode).eq(Member::getGroupRole, groupRole);
		return baseMapper.selectList(memberQueryWrapper);
	}

	@Override
	public IPage<MemberOrderVO> getMemberOrderVO(IPage<Orders> orderPage, Integer status, String queryText) {
		// 2. 從訂單分頁對象提取memberId 成為List
		List<Long> memberIdList = orderPage.getRecords().stream().map(Orders::getMemberId).collect(Collectors.toList());

		if (CollectionUtils.isEmpty(memberIdList)) {
			return new Page<>(); // 沒有符合的訂單，返回空分頁對象
		}

		// 用 memberIdList 查询 member 表，要先符合MemberId表且如果queryText不為空，則模糊查詢姓名、Email和帳號末五碼
		LambdaQueryWrapper<Member> memberQueryWrapper = new LambdaQueryWrapper<>();
		memberQueryWrapper.in(Member::getMemberId, memberIdList)
				.and(StringUtils.isNotBlank(queryText),
						wrapper -> wrapper.like(Member::getFirstName, queryText)
								.or()
								.like(Member::getLastName, queryText)
								.or()
								.like(Member::getEmail, queryText)
								.or()
								.like(Member::getRemitAccountLast5, queryText));

		List<Member> memberList = baseMapper.selectList(memberQueryWrapper);

		// 建立兩個映射 (Map) 以便後續資料整合
		// ordersMap：利用 groupingBy 將 Orders 按 memberId 分組，結果為 Map<Long, List<Orders>>。
		/**
		 * 方法： Collectors.groupingBy() 是一個很常用的收集器（collector）方法，它將集合中的元素根據某個條件（這裡是
		 * Orders::getMemberId）進行分組，並返回一個 Map，其鍵是分組的依據（memberId），值是分組後的集合（這裡是
		 * List<Orders>）。 用途：這個 Map 的目的是將訂單（Orders）資料按 memberId 進行分組，使得每個會員的訂單可以集中在一起。
		 * 使用原因：每個會員可能有多個訂單，因此需要將多個訂單放在同一個 List 中，並且按 memberId 分組。這是使用 groupingBy
		 * 的原因，它非常適合這種需求。
		 */
		Map<Long, List<Orders>> ordersMap = orderPage.getRecords()
				.stream()
				.collect(Collectors.groupingBy(Orders::getMemberId));

		// memberMap：使用 .toMap() 以 memberId 為鍵，Member 物件本身為值，快速查找會員資料。
		/**
		 * 方法：Collectors.toMap() 用於將集合中的每個元素轉換成一個鍵值對，並生成一個 Map。這裡的鍵是
		 * Member::getMemberId，而值是 Member 物件本身。 用途：這個 Map 的目的是將每個 Member 物件與其 memberId
		 * 進行映射，並保證可以通過 memberId 快速找到對應的 Member 資料。 使用原因：在查詢 Member 資料後，我們需要快速查找某個
		 * memberId 對應的 Member 資料，使用 toMap 能夠直接將 Member 和 memberId
		 * 做一一對應，從而可以迅速獲取會員的詳細資訊。
		 */
		Map<Long, Member> memberMap = memberList.stream().collect(Collectors.toMap(Member::getMemberId, m -> m));

		// 整合資料並轉為 MemberOrderVO
		List<MemberOrderVO> voList = memberIdList.stream().map(memberId -> {
			// 查詢每個會員的詳細資料
			Member member = memberMap.get(memberId);
			// 如果會員資料為 null，直接返回 null（代表此 memberId 在 Member 表中找不到）。
			if (member == null) {
				return null;
			}

			// MapStruct直接轉換大部分屬性至VO
			MemberOrderVO vo = memberConvert.entityToMemberOrderVO(member);

			// 確保即使某會員沒有訂單，也不會出錯。
			vo.setOrdersList(ordersMap.getOrDefault(memberId, new ArrayList<>()));

			return vo;
			// 過濾掉 null 的 VO； 匯總成 List。
		}).filter(Objects::nonNull).collect(Collectors.toList());

		/**
		 * 組裝分頁對象返回， new Page<>(...)：建立一個新的分頁對象，並設定： ordersPage.getCurrent()：當前頁碼。
		 * ordersPage.getSize()：每頁大小。 ordersPage.getTotal()：總記錄數。
		 * .setRecords(voList)：將組裝完成的 MemberOrderVO 資料設置到結果頁中。
		 */

		IPage<MemberOrderVO> resultPage = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
		resultPage.setRecords(voList);

		return resultPage;
	}

	@Override
	public IPage<MemberTagVO> getUnpaidMemberPage(Page<Member> page, List<Orders> orderList, String country,
			String queryText) {
		// 1.從訂單表中提取出會員ID 列表
		Set<Long> memberIdSet = orderList.stream().map(orders -> orders.getMemberId()).collect(Collectors.toSet());

		// 2. 將訂單列表轉換為 Map (Key: MemberId, Value: TotalAmount)
		// 使用 (v1, v2) -> v1 處理同一個會員若有多筆未繳費訂單時，取第一筆金額
		Map<Long, BigDecimal> memberAmountMap = orderList.stream()
				.collect(Collectors.toMap(Orders::getMemberId, Orders::getTotalAmount,
						(existing, replacement) -> existing));

		// 判斷是否為本國籍
		Boolean isNational = CountryUtil.isNational(country);

		// 如果會員ID不為Null 以及 集合內元素不為空
		if (memberIdSet != null && !memberIdSet.isEmpty()) {

			// 有 '註冊費' 這張訂單且處於未繳費的 memberIdList，且如果有額外查詢資料 or 進行模糊查詢
			LambdaQueryWrapper<Member> memberWrapper = new LambdaQueryWrapper<>();
			memberWrapper.in(Member::getMemberId, memberIdSet)
					// 如果「本國人」,加搜尋條件,找國家 為 Taiwan的資料
					.eq(isNational, Member::getCountry, CountryUtil.getHomeCountry())
					// 如果「外國人」,加搜尋條件,找國家「不為」Taiwan的資料
					.ne(!isNational, Member::getCountry, CountryUtil.getHomeCountry())
					.and(StringUtils.isNotBlank(queryText), wrapper -> {
						wrapper.like(Member::getRemitAccountLast5, queryText)
								.or()
								.like(Member::getChineseName, queryText)
								.or()
								.like(Member::getIdCard, queryText);
					});

			// 獲得 member 的分頁對象
			Page<Member> memberPage = baseMapper.selectPage(page, memberWrapper);

			// 5. 將 Member 轉換為 MemberTagVO，並從 Map 中取出對應金額
			List<MemberTagVO> voList = memberPage.getRecords().stream().map(member -> {
				// 使用實體轉換工具
				MemberTagVO vo = memberConvert.entityToMemberTagVO(member);

				// 從預先準備好的 Map 獲取金額，不再調用 ordersService
				BigDecimal amount = memberAmountMap.getOrDefault(member.getMemberId(), BigDecimal.ZERO);

				vo.setAmount(amount);
				vo.setStatus(OrderStatusEnum.UNPAID.getValue());
				vo.setTagList(Collections.emptyList());

				return vo;
			}).collect(Collectors.toList());

			// 6. 組裝分頁結果
			IPage<MemberTagVO> resultPage = new Page<>(memberPage.getCurrent(), memberPage.getSize(),
					memberPage.getTotal());
			resultPage.setRecords(voList);

			return resultPage;

		}

		return null;
	}

	@Override
	public int getMemberGroupIndex(int groupSize) {
		Long memberCount = baseMapper.selectCount(null);
		return (int) Math.ceil(memberCount / (double) groupSize);
	}

	@Override
	public int getMemberCategoryGroupIndex(int groupSize, Integer memberCategory) {

		LambdaQueryWrapper<Member> queryWrapper = new LambdaQueryWrapper<>();
		queryWrapper.eq(Member::getCategory, memberCategory);

		Long memberCategoryCount = baseMapper.selectCount(queryWrapper);
		return (int) Math.ceil(memberCategoryCount / (double) groupSize);
	}

	/**
	 * 判斷Email 及 身分證字號 是否被重複註冊,沒有則新增
	 * 
	 * @param member
	 */
	private void validateAndAddMember(Member member) {

		// 判斷email是否重複註冊
		LambdaQueryWrapper<Member> emailQueryWrapper = new LambdaQueryWrapper<>();
		emailQueryWrapper.eq(Member::getEmail, member.getEmail());
		Long emailCount = baseMapper.selectCount(emailQueryWrapper);

		if (emailCount > 0) {
			throw new RegisteredAlreadyExistsException(
					messageHelper.get(I18nMessageKey.Registration.Auth.ACCOUNT_REGISTERED));
		}

		if (member.getIdCard() != null) {
			// 判斷身分證是否重複註冊
			LambdaQueryWrapper<Member> idCardQueryWrapper = new LambdaQueryWrapper<>();
			idCardQueryWrapper.eq(Member::getIdCard, member.getIdCard());
			Long idCardCount = baseMapper.selectCount(idCardQueryWrapper);

			if (idCardCount > 0) {
				throw new RegisteredAlreadyExistsException(
						messageHelper.get(I18nMessageKey.Registration.Auth.ACCOUNT_REGISTERED));
			}
		}

		baseMapper.insert(member);

	}

	@Override
	public Member addMember(AddMemberDTO addMemberDTO) {
		// 1.資料轉換
		Member currentMember = memberConvert.addDTOToEntity(addMemberDTO);
		// 2.使用驗證並新增
		this.validateAndAddMember(currentMember);
		// 3.返回Member資料
		return currentMember;
	}

	@Override
	public Member addMemberForAdmin(AddMemberForAdminDTO addMemberForAdminDTO) {
		// 1.資料轉換
		Member currentMember = memberConvert.forAdminAddDTOToEntity(addMemberForAdminDTO);
		// 2.使用驗證並新增
		this.validateAndAddMember(currentMember);
		// 3.返回Member資料
		return currentMember;

	}

	@Override
	public Member addMemberByRoleAndGroup(String groupCode, String groupRole, AddGroupMemberDTO addGroupMemberDTO) {
		Member member = memberConvert.addGroupDTOToEntity(addGroupMemberDTO);
		member.setGroupCode(groupCode);
		member.setGroupRole(groupRole);
		baseMapper.insert(member);
		return member;
	}

	/**
	 * 現場報到時，新增會員
	 * 
	 * @param walkInRegistrationDTO
	 * @return
	 */
	@Override
	public Member addMemberOnSite(WalkInRegistrationDTO walkInRegistrationDTO) {

		Member member = new Member();
		member.setEmail(walkInRegistrationDTO.getEmail());
		member.setChineseName(walkInRegistrationDTO.getChineseName());
		member.setFirstName(walkInRegistrationDTO.getFirstName());
		member.setLastName(walkInRegistrationDTO.getLastName());
		member.setCategory(walkInRegistrationDTO.getCategory());

		//判斷Email有無被註冊過
		LambdaQueryWrapper<Member> memberQueryWrapper = new LambdaQueryWrapper<>();
		memberQueryWrapper.eq(Member::getEmail, member.getEmail());
		Long memberCount = baseMapper.selectCount(memberQueryWrapper);

		if (memberCount > 0) {
			throw new RegisteredAlreadyExistsException(
					messageHelper.get(I18nMessageKey.Registration.Auth.EMAIL_REGISTERED));
		}

		baseMapper.insert(member);

		return member;
	};

	@Override
	public void updateMember(PutMemberDTO putMemberDTO) {
		Member newMemberInfo = memberConvert.putDTOToEntity(putMemberDTO);
		Member oldMemberInfo = this.getMember(newMemberInfo.getMemberId());

		// 抽出這次更新時的國籍
		String oldMemberCountry = CountryUtil.getTaiwanOrForeign(oldMemberInfo.getCountry());
		String newMemberCountry = CountryUtil.getTaiwanOrForeign(newMemberInfo.getCountry());

		// 如果國籍有變更 國外=>台灣 or 台灣=>國外 則拒絕變更
		if (!oldMemberCountry.equals(newMemberCountry)) {
			throw new MemberException("Nationality cannot be changed between domestic and foreign statuses.");
		}

		String oldMemberIdCard = oldMemberInfo.getIdCard();
		String newMemberIdCard = newMemberInfo.getIdCard();

		System.out.println("舊ID_Card: " + oldMemberIdCard);
		System.out.println("新ID_Card: " + newMemberIdCard);

		// 台灣人註冊
		if (CountryUtil.isNational(newMemberCountry)) {
			// idCard有傳值，且跟舊資料不一致，idCard不許修改,因為這代表帳號
			if (newMemberIdCard != null && !oldMemberIdCard.equals(newMemberIdCard)) {
				throw new MemberException("身分證不允許更新，如需更新請洽工作人員");
			}
		}

		if (newMemberInfo.getIdCard() != null) {
			// 判斷要更新的身分證，是否已被註冊
			LambdaQueryWrapper<Member> idCardQueryWrapper = new LambdaQueryWrapper<>();
			idCardQueryWrapper.eq(Member::getIdCard, newMemberInfo.getIdCard());
			Long idCardCount = baseMapper.selectCount(idCardQueryWrapper);

			if (idCardCount > 0) {
				throw new RegisteredAlreadyExistsException(
						messageHelper.get(I18nMessageKey.Registration.Auth.ID_CARD_REGISTERED));
			}
		}

		// 如果前述條件都通過,進行更新
		baseMapper.updateById(newMemberInfo);

	}

	@Override
	public void updateMemberForAdmin(PutMemberForAdminDTO putMemberForAdminDTO) {
		Member member = memberConvert.putForAdminDTOToEntity(putMemberForAdminDTO);
		baseMapper.updateById(member);
	}

	@Override
	public void deleteMember(Long memberId) {
		baseMapper.deleteById(memberId);
	}

	@Override
	public void deleteMemberList(List<Long> memberIds) {
		baseMapper.deleteBatchIds(memberIds);
	}

	/** 以下跟登入有關 */

	@Override
	public SaTokenInfo login(Member member) {
		// 之後應該要以這個會員ID 產生Token 回傳前端，讓他直接進入登入狀態
		StpKit.MEMBER.login(member.getMemberId());

		// 登入後才能取得session
		SaSession session = StpKit.MEMBER.getSession();
		// 並對此token 設置會員的緩存資料
		session.set(MEMBER_CACHE_INFO_KEY, member);

		SaTokenInfo tokenInfo = StpKit.MEMBER.getTokenInfo();
		return tokenInfo;
	}

	/**
	 * 透過Member資訊,返回TokenInfo
	 * 
	 * @param member
	 * @return
	 */
	private SaTokenInfo returnSaTokenInfo(Member member) {
		// 之後應該要以這個會員ID 產生Token 回傳前端，讓他直接進入登入狀態
		StpKit.MEMBER.login(member.getMemberId());
		// 登入後才能取得session
		SaSession session = StpKit.MEMBER.getSession();
		// 並對此token 設置會員的緩存資料
		session.set(MEMBER_CACHE_INFO_KEY, member);
		return StpKit.MEMBER.getTokenInfo();
	}

	@Override
	public SaTokenInfo login(MemberEmailLogin memberLoginInfo) {
		LambdaQueryWrapper<Member> memberQueryWrapper = new LambdaQueryWrapper<>();
		memberQueryWrapper.eq(Member::getEmail, memberLoginInfo.getEmail())
				.eq(Member::getPassword, memberLoginInfo.getPassword());

		Member member = baseMapper.selectOne(memberQueryWrapper);

		if (member != null) {
			return this.returnSaTokenInfo(member);
		}

		// 如果 member為null , 則直接拋出異常
		throw new AccountPasswordWrongException(messageHelper.get(I18nMessageKey.Registration.Auth.WRONG_ACCOUNT));

	}

	@Override
	public SaTokenInfo login(MemberIdCardLogin memberIdCardLogin) {
		// 透過idCard 和 password 查詢Member資訊
		LambdaQueryWrapper<Member> memberQueryWrapper = new LambdaQueryWrapper<>();
		memberQueryWrapper.eq(Member::getIdCard, memberIdCardLogin.getIdCard())
				.eq(Member::getPassword, memberIdCardLogin.getPassword());

		Member member = baseMapper.selectOne(memberQueryWrapper);

		if (member != null) {
			return this.returnSaTokenInfo(member);
		}

		// 如果 member為null , 則直接拋出異常
		throw new AccountPasswordWrongException(messageHelper.get(I18nMessageKey.Registration.Auth.WRONG_ACCOUNT));

	}

	@Override
	public SaTokenInfo foreignLogin(MemberLoginDTO memberLoginDTO) {

		// 獲得本國國籍 <Taiwan>
		String national = CountryUtil.getHomeCountry();

		// 除了帳號和密碼，額外判斷國家不屬於 台灣
		LambdaQueryWrapper<Member> memberQueryWrapper = new LambdaQueryWrapper<>();
		memberQueryWrapper.eq(Member::getEmail, memberLoginDTO.getAccount())
				.eq(Member::getPassword, memberLoginDTO.getPassword())
				.ne(Member::getCountry, national);

		Member member = baseMapper.selectOne(memberQueryWrapper);

		if (member != null) {
			return this.returnSaTokenInfo(member);
		}

		// 如果 member為null , 則直接拋出異常
		throw new AccountPasswordWrongException(messageHelper.get(I18nMessageKey.Registration.Auth.WRONG_ACCOUNT));

	}

	@Override
	public SaTokenInfo localLogin(MemberLoginDTO memberLoginDTO) {
		// 獲得本國國籍 <Taiwan>
		String national = CountryUtil.getHomeCountry();

		// 除了帳號和密碼，額外判斷國家屬於 台灣
		LambdaQueryWrapper<Member> memberQueryWrapper = new LambdaQueryWrapper<>();
		memberQueryWrapper.eq(Member::getIdCard, memberLoginDTO.getAccount())
				.eq(Member::getPassword, memberLoginDTO.getPassword())
				.eq(Member::getCountry, national);

		Member member = baseMapper.selectOne(memberQueryWrapper);

		if (member != null) {
			return this.returnSaTokenInfo(member);
		}

		// 如果 member為null , 則直接拋出異常
		throw new AccountPasswordWrongException(messageHelper.get(I18nMessageKey.Registration.Auth.WRONG_ACCOUNT));

	}

	@Override
	public void logout() {
		// 根據token 直接做登出
		StpKit.MEMBER.logout();

	}

	@Override
	public Member getMemberInfo() {
		// 會員登入後才能取得session
		SaSession session = StpKit.MEMBER.getSession();
		// 獲取當前使用者的資料
		Member memberInfo = (Member) session.get(MEMBER_CACHE_INFO_KEY);
		return memberInfo;
	}

	/** ----------------------------以下跟Tag有關---------------------------------- */
	@Override
	public MemberTagVO getMemberTagVOByMember(Long memberId) {
		// 獲取member 資料並轉換成 memberTagVO
		Member member = baseMapper.selectById(memberId);
		return memberConvert.entityToMemberTagVO(member);
	}

	@Override
	public IPage<Member> getMemberPageByQuery(Page<Member> page, String queryText, Collection<Long> memberIds) {
		// 1.基於條件查詢 memberList
		LambdaQueryWrapper<Member> memberWrapper = new LambdaQueryWrapper<>();

		// 2.當 queryText 不為空字串、空格字串、Null 時才加入篩選條件
		memberWrapper
				.and(StringUtils.isNotBlank(queryText),
						wrapper -> wrapper.like(Member::getFirstName, queryText)
								.or()
								.like(Member::getLastName, queryText)
								.or()
								.like(Member::getChineseName, queryText)
								.or()
								.like(Member::getEmail, queryText)
								.or()
								.like(Member::getPhone, queryText)
								.or()
								.like(Member::getRemitAccountLast5, queryText))
				.in(!memberIds.isEmpty(), Member::getMemberId, memberIds);

		// 3.查詢 MemberPage (分頁)
		return baseMapper.selectPage(page, memberWrapper);
	}

	@Override
	public Map<Long, Member> getMemberMap() {
		// 1.高效獲取所有會員
		List<Member> members = this.getMembersEfficiently();
		// 2.返回key為 memberId, value為Member 的Map 對象
		return members.stream().collect(Collectors.toMap(Member::getMemberId, Function.identity()));
	}

	@Override
	public Map<Long, Member> getMemberMapByIds(Collection<Long> memberIds) {
		// 1.用memberId列表查詢Member資料
		List<Member> memberList = this.getMemberListByIds(memberIds);
		// 2.Member資料轉為memberId為key , Member本身為值的Map對象
		return memberList.stream().collect(Collectors.toMap(Member::getMemberId, Function.identity()));
	}

	@Override
	public Map<Long, Member> getMemberMapByAttendeesList(Collection<Attendees> attendeesList) {
		// 1.提取attendees的memberId,拿到memberId列表
		Set<Long> memberIdSet = attendeesList.stream().map(Attendees::getMemberId).collect(Collectors.toSet());
		// 2.用memberId列表查詢Member資料
		List<Member> memberList = this.getMemberListByIds(memberIdSet);
		// 3.Member資料轉為memberId為key , Member本身為值的Map對象
		return memberList.stream().collect(Collectors.toMap(Member::getMemberId, Function.identity()));
	}

	@Override
	public SaTokenInfo quickLogin(String email) {
		// 僅判斷 email 是否有存在 
		LambdaQueryWrapper<Member> memberQueryWrapper = new LambdaQueryWrapper<>();
		memberQueryWrapper.eq(Member::getEmail, email);
		Member member = baseMapper.selectOne(memberQueryWrapper);

		if (member != null) {
			return this.returnSaTokenInfo(member);
		}

		// 如果 member為null , 則直接拋出異常
		throw new AccountPasswordWrongException(messageHelper.get(I18nMessageKey.Registration.Auth.WRONG_ACCOUNT));
	}

}
