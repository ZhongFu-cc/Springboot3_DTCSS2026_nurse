package tw.org.dtcss.strategy.project;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import tw.org.dtcss.pojo.DTO.EmailBodyContent;
import tw.org.dtcss.pojo.entity.Attendees;
import tw.org.dtcss.pojo.entity.Member;
import tw.org.dtcss.pojo.entity.Orders;
import tw.org.dtcss.pojo.entity.Tag;
import tw.org.dtcss.service.AsyncService;
import tw.org.dtcss.service.AttendeesService;
import tw.org.dtcss.service.AttendeesTagService;
import tw.org.dtcss.service.NotificationService;
import tw.org.dtcss.service.OrdersService;
import tw.org.dtcss.service.TagService;

@Component
@RequiredArgsConstructor
public class FreeModeStrategy implements ProjectModeStrategy {

	@Value("${project.name}")
	private String PROJECT_NAME;

	@Value("${project.banner-url}")
	private String BANNER_PHOTO_URL;

	@Value("${project.group-size}")
	private int GROUP_SIZE;

	private final OrdersService ordersService;
	private final AttendeesService attendeesService;
	private final TagService tagService;
	private final AttendeesTagService attendeesTagService;
	private final NotificationService notificationService;
	private final AsyncService asyncService;

	@Override
	public void handleRegistration(Member member) {

		// 1.創建「免費」註冊費訂單，狀態為 「已付款」
		Orders registrationOrder = ordersService.createFreeRegistrationOrder(member);

		// 2.創建註冊成功通知信件內容
		EmailBodyContent registrationSuccessContent = notificationService.generateRegistrationSuccessContent(member,
				BANNER_PHOTO_URL,registrationOrder);

		// 3.異步寄送信件
		asyncService.sendCommonEmail(member.getEmail(), PROJECT_NAME + " Registration Successful",
				registrationSuccessContent.getHtmlContent(), registrationSuccessContent.getPlainTextContent());

		// 4.新增進與會者名單
		Attendees attendees = attendeesService.addAttendees(member);

		// 5.獲取當下 Attendees 群體的Index,用於後續標籤分組
		int attendeesGroupIndex = attendeesService.getAttendeesGroupIndex(GROUP_SIZE);

		// 6.與會者標籤分組
		// 拿到 Tag（不存在則新增Tag）
		Tag attendeesGroupTag = tagService.getOrCreateAttendeesGroupTag(attendeesGroupIndex);
		// 關聯 Attendees 與 Tag
		attendeesTagService.addAttendeesTag(attendees.getAttendeesId(), attendeesGroupTag.getTagId());

	}

	@Override
	public void handleGroupRegistration(Member member, boolean isMaster, BigDecimal totalFee) {

		// 1.創建 「免費」 團體註冊費訂單，狀態為 「已付款」
		ordersService.createFreeGroupRegistrationPaidOrder(member);

		// 2.產生系統團體報名通知信
		EmailBodyContent groupRegistrationSuccessContent = notificationService
				.generateGroupRegistrationSuccessContent(member, BANNER_PHOTO_URL);

		// 3.寄信個別通知會員，團體報名成功
		asyncService.sendCommonEmail(member.getEmail(), PROJECT_NAME + " GROUP Registration Successful",
				groupRegistrationSuccessContent.getHtmlContent(),
				groupRegistrationSuccessContent.getPlainTextContent());

		// 4.新增進與會者名單
		Attendees attendees = attendeesService.addAttendees(member);

		// 5.獲取當下 Attendees 群體的Index,用於後續標籤分組
		int attendeesGroupIndex = attendeesService.getAttendeesGroupIndex(GROUP_SIZE);

		// 6.與會者標籤分組
		// 拿到 Tag（不存在則新增Tag）
		Tag attendeesGroupTag = tagService.getOrCreateAttendeesGroupTag(attendeesGroupIndex);
		// 關聯 Attendees 與 Tag
		attendeesTagService.addAttendeesTag(attendees.getAttendeesId(), attendeesGroupTag.getTagId());

	}

	@Override
	public void handlePaperSubmission(Long memberId) {
		// Free 模式,不用去攔截他投稿
		
		
	}

}
