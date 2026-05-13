package tw.org.dtcss.strategy.project;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import tw.org.dtcss.config.RegistrationFeeConfig;
import tw.org.dtcss.constants.I18nMessageKey;
import tw.org.dtcss.enums.MemberCategoryEnum;
import tw.org.dtcss.enums.OrderStatusEnum;
import tw.org.dtcss.enums.RegistrationPhaseEnum;
import tw.org.dtcss.exception.PaperClosedException;
import tw.org.dtcss.helper.MessageHelper;
import tw.org.dtcss.helper.TagAssignmentHelper;
import tw.org.dtcss.pojo.DTO.EmailBodyContent;
import tw.org.dtcss.pojo.entity.Attendees;
import tw.org.dtcss.pojo.entity.Member;
import tw.org.dtcss.pojo.entity.Orders;
import tw.org.dtcss.pojo.entity.Tag;
import tw.org.dtcss.service.AsyncService;
import tw.org.dtcss.service.AttendeesService;
import tw.org.dtcss.service.AttendeesTagService;
import tw.org.dtcss.service.MemberTagService;
import tw.org.dtcss.service.NotificationService;
import tw.org.dtcss.service.OrdersService;
import tw.org.dtcss.service.SettingService;
import tw.org.dtcss.service.TagService;
import tw.org.dtcss.utils.CountryUtil;

@Component
@RequiredArgsConstructor
public class PrepaidModeStrategy implements ProjectModeStrategy {

	@Value("${project.name}")
	private String PROJECT_NAME;

	@Value("${project.banner-url}")
	private String BANNER_PHOTO_URL;

	@Value("${project.group-size}")
	private int GROUP_SIZE;

	private final RegistrationFeeConfig registrationFeeConfig;

	private final MessageHelper messageHelper;
	private final TagAssignmentHelper tagAssignmentHelper;
	private final MemberTagService memberTagService;
	private final AttendeesService attendeesService;
	private final TagService tagService;
	private final AttendeesTagService attendeesTagService;
	private final OrdersService ordersService;
	private final SettingService settingService;
	private final NotificationService notificationService;
	private final AsyncService asyncService;

	/**
	 * 預付款模式<br>
	 * 計算會員的註冊費，建立訂單，並寄信通知
	 * 
	 */
	@Override
	public void handleRegistration(Member member) {

		// 1.拿到配置設定,知道處於哪個註冊階段
		RegistrationPhaseEnum registrationPhaseEnum = settingService.getRegistrationPhaseEnum();

		// 2.透過Country 拿到國籍 , 只分國內國外,	
		String country = CountryUtil.getTaiwanOrForeign(member.getCountry());

		// 3.拿到身分
		MemberCategoryEnum memberCategoryEnum = MemberCategoryEnum.fromValue(member.getCategory());

		// 4.透過階段、國籍、身分，得到金額
		BigDecimal membershipFee = registrationFeeConfig.getFee(registrationPhaseEnum.getValue(), country,
				memberCategoryEnum.getConfigKey());

		Orders registrationOrder ;
		
		// 5.如果註冊費金額為0 , 創建免費註冊費訂單 , 會自動為繳費完畢的情況
		if (membershipFee.compareTo(BigDecimal.ZERO) == 0) {
			registrationOrder = ordersService.createFreeRegistrationOrder(member);

			// 5-1.自動繳費完畢,新增進與會者名單
			Attendees attendees = attendeesService.addAttendees(member);

			// 5-2.獲取當下 Attendees 群體的Index,用於後續標籤分組
			int attendeesGroupIndex = attendeesService.getAttendeesGroupIndex(GROUP_SIZE);

			// 5-3.與會者標籤分組
			// 拿到 Tag（不存在則新增Tag）
			Tag attendeesGroupTag = tagService.getOrCreateAttendeesGroupTag(attendeesGroupIndex);
			// 關聯 Attendees 與 Tag
			attendeesTagService.addAttendeesTag(attendees.getAttendeesId(), attendeesGroupTag.getTagId());

		} else {
			// 創建付費註冊費訂單
			registrationOrder = ordersService.createRegistrationOrder(membershipFee, member);
			// 獲取當下「未付款」的Member群體的Index，賦予「未繳費」標籤
			tagAssignmentHelper.assignTag(member.getMemberId(), ordersService::getNotPaidRegistrationOrderGroupIndex,
					tagService::getOrCreateNotPaidGroupTag, memberTagService::addMemberTag);
		}

		// 6.創建註冊成功通知信件內容
		EmailBodyContent registrationSuccessContent = notificationService.generateRegistrationSuccessContent(member,
				BANNER_PHOTO_URL,registrationOrder);

		// 7.異步寄送信件
		asyncService.sendCommonEmail(member.getEmail(), PROJECT_NAME + " Registration Successful",
				registrationSuccessContent.getHtmlContent(), registrationSuccessContent.getPlainTextContent());

	}

	@Override
	public void handleGroupRegistration(Member member, boolean isMaster, BigDecimal totalFee) {
		if (isMaster) {
			// Master 負責付錢
			ordersService.createGroupRegistrationOrder(totalFee, member);
			// 獲取當下「未付款」的Member群體的Index，賦予「未繳費」標籤
			tagAssignmentHelper.assignTag(member.getMemberId(), ordersService::getNotPaidRegistrationOrderGroupIndex,
					tagService::getOrCreateNotPaidGroupTag, memberTagService::addMemberTag);
		} else {
			// Slave 不付錢，0元訂單，未付款
			ordersService.createFreeGroupRegistrationOrder(member);
			// 獲取當下「未付款」的Member群體的Index，賦予「未繳費」標籤
			tagAssignmentHelper.assignTag(member.getMemberId(), ordersService::getNotPaidRegistrationOrderGroupIndex,
					tagService::getOrCreateNotPaidGroupTag, memberTagService::addMemberTag);
		}

		// 2.產生系統團體報名通知信
		EmailBodyContent groupRegistrationSuccessContent = notificationService
				.generateGroupRegistrationSuccessContent(member, BANNER_PHOTO_URL);

		// 3.寄信個別通知會員，團體報名成功
		asyncService.sendCommonEmail(member.getEmail(), PROJECT_NAME + " GROUP Registration Successful",
				groupRegistrationSuccessContent.getHtmlContent(),
				groupRegistrationSuccessContent.getPlainTextContent());

	}

	@Override
	public void handlePaperSubmission(Long memberId) {
		//「先付費」 模式,需要先判斷他有沒有繳錢

		// 1.查詢註冊費訂單,個人和團體報名都算
		Orders registrationOrder = ordersService.getRegistrationOrderByMemberId(memberId);

		// 2.如果這個訂單 沒有付款成功, 那直接報錯,請他先去付費
		if (!OrderStatusEnum.PAYMENT_SUCCESS.getValue().equals(registrationOrder.getStatus())) {
			throw new PaperClosedException(messageHelper.get(I18nMessageKey.Paper.PREPAID));
		}

	}

}
