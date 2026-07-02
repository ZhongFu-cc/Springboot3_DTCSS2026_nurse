package tw.org.dtcss.manager;

import org.springframework.stereotype.Component;

import cn.dev33.satoken.stp.SaTokenInfo;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import tw.org.dtcss.exception.MemberException;
import tw.org.dtcss.pojo.DTO.EmailBodyContent;
import tw.org.dtcss.pojo.DTO.MemberEmailLogin;
import tw.org.dtcss.pojo.DTO.MemberIdCardLogin;
import tw.org.dtcss.pojo.DTO.MemberLoginDTO;
import tw.org.dtcss.pojo.entity.Member;
import tw.org.dtcss.service.AsyncService;
import tw.org.dtcss.service.MemberService;
import tw.org.dtcss.service.NotificationService;
import tw.org.dtcss.service.SettingService;

@RequiredArgsConstructor
@Component
public class MemberAuthManager {

	private final MemberService memberService;
	private final NotificationService notificationService;
	private final AsyncService asyncService;
	private final SettingService settingService;

	/**
	 * 會員登入 - Only Email
	 * 僅在活動日期間開放
	 * 
	 * @param email
	 * @return
	 */
	public SaTokenInfo onlyEmailLogin(@NotBlank String email) {
		// 判斷是不是處於活動日期間
		Boolean isDuringEventPeriod = settingService.isDuringEventPeriod();
		if (isDuringEventPeriod) {
			return memberService.quickLogin(email);
		} else {
			throw new MemberException("功能未開放");
		}

	};

	/**
	 * 會員登入 - Email & Password
	 * 
	 * @param memberEmailLogin
	 * @return
	 */
	public SaTokenInfo login(MemberEmailLogin memberEmailLogin) {
		return memberService.login(memberEmailLogin);
	}

	/**
	 * 會員登入 - IdCard & Password
	 * 
	 * @param memberIdCardLogin
	 * @return
	 */
	public SaTokenInfo login(MemberIdCardLogin memberIdCardLogin) {
		return memberService.login(memberIdCardLogin);
	}

	/**
	 * 「外國人」登入 - Email & Password 綁定國籍「非」台灣
	 * 
	 * @param memberLoginDTO
	 * @return
	 */
	public SaTokenInfo foreignLogin(MemberLoginDTO memberLoginDTO) {
		return memberService.foreignLogin(memberLoginDTO);
	}

	/**
	 * 「本國人」登入 - IdCard & Password 綁定國籍 台灣
	 * 
	 * @param memberLoginDTO
	 * @return
	 */
	public SaTokenInfo localLogin(MemberLoginDTO memberLoginDTO) {
		return memberService.localLogin(memberLoginDTO);
	}

	/**
	 * 會員登出
	 * 
	 */
	public void logout() {
		memberService.logout();
	};

	/**
	 * 忘記密碼
	 * 
	 * @param email
	 */
	public void forgetPassword(String email) {

		// 1.先透過email查找是否為註冊過的會員
		Member member = memberService.getMemberByEmail(email);

		// 2.產生找回密碼的信件內容
		EmailBodyContent retrieveContent = notificationService.generateRetrieveContent(member.getPassword());

		// 3.將密碼寄送到信箱
		asyncService.sendCommonEmail(email, "Retrieve password", retrieveContent.getHtmlContent(),
				retrieveContent.getPlainTextContent());

	}

	public Member getMemberInfo() {
		return memberService.getMemberInfo();
	}

}
