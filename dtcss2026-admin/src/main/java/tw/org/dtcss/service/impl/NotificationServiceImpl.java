package tw.org.dtcss.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import lombok.RequiredArgsConstructor;
import tw.org.dtcss.enums.MemberCategoryEnum;
import tw.org.dtcss.enums.ProjectModeEnum;
import tw.org.dtcss.pojo.DTO.EmailBodyContent;
import tw.org.dtcss.pojo.entity.Member;
import tw.org.dtcss.pojo.entity.Orders;
import tw.org.dtcss.pojo.entity.Paper;
import tw.org.dtcss.service.NotificationService;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

	private final SpringTemplateEngine templateEngine;

	@Value("${project.name}")
	private String PROJECT_NAME;

	@Value("${project.domain}")
	private String DOMAIN;

	@Value("${project.email.reply-to}")
	private String REPLY_TO;

	@Value("${project.banner-url}")
	private String BANNER_PHOTO_URL;

	@Value("${project.language}")
	private String LANGUAGE;

	@Value("${project.mode}")
	private ProjectModeEnum mode;

	/**
	 * 固定通用的信件變量
	 */
	private static final String FIELD_DOMAIN = "domain";
	private static final String FIELD_BANNER_PHOTO_URL = "bannerPhotoUrl";
	private static final String FIELD_CONFERENCE_NAME = "conferenceName";
	private static final String FIELD_UPDATE_TIME = "updateTime";
	private static final String FIELD_CURRENT_YEAR = "currentYear";
	private static final String FIELD_REPLY_TO = "replyTo";
	private static final String FIELD_MODE = "mode";

	/**
	 * 註冊通知使用的信件變量
	 */
	private static final String FIELD_FIRST_NAME = "firstName";
	private static final String FIELD_LAST_NAME = "lastName";
	private static final String FIELD_COUNTRY = "country";
	private static final String FIELD_AFFILIATION = "affiliation";
	private static final String FIELD_JOB_TITLE = "jobTitle";
	private static final String FIELD_PHONE = "phone";
	private static final String FIELD_CATEGORY = "category";

	// 繳費連結
	private static final String FIELD_PAYMENT_AMOUNT = "paymentAmount";
	private static final String FIELD_PAYMENT_URL = "paymentUrl";
	private static final String PREFIX_PAYMENT_URL = "/orders/payment";

	@Override
	public EmailBodyContent generateRegistrationSuccessContent(Member member, String bannerPhotoUrl,
			Orders registrationOrder) {
		Context context = new Context();

		// 1.設置通用變量
		context.setVariable(FIELD_CONFERENCE_NAME, PROJECT_NAME);
		context.setVariable(FIELD_BANNER_PHOTO_URL, bannerPhotoUrl);
		context.setVariable(FIELD_MODE, mode.getValue());
		context.setVariable(FIELD_UPDATE_TIME,
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
		context.setVariable(FIELD_CURRENT_YEAR, String.valueOf(LocalDate.now().getYear()));
		context.setVariable(FIELD_REPLY_TO, REPLY_TO);

		// 2.設置註冊信變量
		context.setVariable(FIELD_FIRST_NAME, member.getFirstName());
		context.setVariable(FIELD_LAST_NAME, member.getLastName());
		context.setVariable(FIELD_COUNTRY, member.getCountry());
		context.setVariable(FIELD_AFFILIATION, member.getAffiliation());
		context.setVariable(FIELD_JOB_TITLE, member.getJobTitle());
		context.setVariable(FIELD_PHONE, member.getPhone());
		// Category 要轉換成字串
		context.setVariable(FIELD_CATEGORY, MemberCategoryEnum.fromValue(member.getCategory()).getLabelEn());

		// compareTo結果:1=大於;0=等於;-1=小於
		// 如果訂單不為0元訂單，設置付費連結;
		if (registrationOrder.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
			context.setVariable(FIELD_PAYMENT_AMOUNT, registrationOrder.getTotalAmount());
			context.setVariable(FIELD_PAYMENT_URL,
					DOMAIN + PREFIX_PAYMENT_URL + "?id=" + registrationOrder.getOrdersId());
		}

		// 3. 根據 project.language 選擇模板路徑（無需 if-else 太多，簡單拼接）
		String languagePath = "";
		if ("zh_TW".equalsIgnoreCase(LANGUAGE)) {
			languagePath = "/zh_tw";
		}
		String htmlTemplatePath = "html" + languagePath + "/registration-success-notification.html";
		String textTemplatePath = "plain-text" + languagePath + "/registration-success-notification.txt";

		// 4.產生具有HTML 和 純文字的兩種信件內容 EmailBodyContent  並返回
		String htmlContent = templateEngine.process(htmlTemplatePath, context);
		String plainTextContent = templateEngine.process(textTemplatePath, context);
		return new EmailBodyContent(htmlContent, plainTextContent);

	}

	@Override
	public EmailBodyContent generateGroupRegistrationSuccessContent(Member member, String bannerPhotoUrl) {
		Context context = new Context();
		// 1.設置通用變量
		context.setVariable(FIELD_CONFERENCE_NAME, PROJECT_NAME);
		context.setVariable(FIELD_BANNER_PHOTO_URL, bannerPhotoUrl);
		context.setVariable(FIELD_MODE, mode.getValue());
		context.setVariable(FIELD_UPDATE_TIME,
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
		context.setVariable(FIELD_CURRENT_YEAR, String.valueOf(LocalDate.now().getYear()));
		context.setVariable(FIELD_REPLY_TO, REPLY_TO);

		// 2.設置註冊信變量
		context.setVariable(FIELD_FIRST_NAME, member.getFirstName());
		context.setVariable(FIELD_LAST_NAME, member.getLastName());
		context.setVariable(FIELD_COUNTRY, member.getCountry());
		context.setVariable(FIELD_AFFILIATION, member.getAffiliation());
		context.setVariable(FIELD_JOB_TITLE, member.getJobTitle());
		context.setVariable(FIELD_PHONE, member.getPhone());
		// Category 要轉換成字串
		context.setVariable(FIELD_CATEGORY, MemberCategoryEnum.fromValue(member.getCategory()).getLabelEn());

		// 3. 根據 project.language 選擇模板路徑（無需 if-else 太多，簡單拼接）
		String languagePath = "";
		if ("zh_TW".equalsIgnoreCase(LANGUAGE)) {
			languagePath = "/zh_tw";
		}
		String htmlTemplatePath = "html" + languagePath + "/group-registration-success-notification.html";
		String textTemplatePath = "plain-text" + languagePath + "/group-registration-success-notification.txt";

		// 4.產生具有HTML 和 純文字的兩種信件內容 EmailBodyContent  並返回
		String htmlContent = templateEngine.process(htmlTemplatePath, context);
		String plainTextContent = templateEngine.process(textTemplatePath, context);
		return new EmailBodyContent(htmlContent, plainTextContent);
	}

	@Override
	public EmailBodyContent generateRetrieveContent(String password) {
		// 1.設置通用變量
		Context context = new Context();
		context.setVariable(FIELD_CONFERENCE_NAME, PROJECT_NAME);
		context.setVariable(FIELD_REPLY_TO, REPLY_TO);
		context.setVariable(FIELD_UPDATE_TIME,
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
		context.setVariable(FIELD_CURRENT_YEAR, String.valueOf(LocalDate.now().getYear()));

		// 2.設置 忘記密碼 變量
		context.setVariable("password", password);

		// 3. 根據 project.language 選擇模板路徑（無需 if-else 太多，簡單拼接）
		String languagePath = "";
		if ("zh_TW".equalsIgnoreCase(LANGUAGE)) {
			languagePath = "/zh_tw";
		}
		String htmlTemplatePath = "html" + languagePath + "/retrieve-password.html";
		String textTemplatePath = "plain-text" + languagePath + "/retrieve-password.txt";

		// 4.產生具有HTML 和 純文字的兩種信件內容 EmailBodyContent  並返回
		String htmlContent = templateEngine.process(htmlTemplatePath, context);
		String plainTextContent = templateEngine.process(textTemplatePath, context);

		return new EmailBodyContent(htmlContent, plainTextContent);

	}

	@Override
	public EmailBodyContent generateAbstractSuccessContent(Paper paper) {

		// 1.設置通用變量
		Context context = new Context();
		context.setVariable(FIELD_BANNER_PHOTO_URL, BANNER_PHOTO_URL);
		context.setVariable(FIELD_CONFERENCE_NAME, PROJECT_NAME);
		context.setVariable(FIELD_REPLY_TO, REPLY_TO);
		context.setVariable(FIELD_UPDATE_TIME,
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
		context.setVariable(FIELD_CURRENT_YEAR, String.valueOf(LocalDate.now().getYear()));

		// 2.設置 稿件 變量
		context.setVariable("paper", paper);

		// 3. 根據 project.language 選擇模板路徑（無需 if-else 太多，簡單拼接）
		String languagePath = "";
		if ("zh_TW".equalsIgnoreCase(LANGUAGE)) {
			languagePath = "/zh_tw";
		}
		String htmlTemplatePath = "html" + languagePath + "/abstract-success-notification.html";
		String textTemplatePath = "plain-text" + languagePath + "/abstract-success-notification.txt";

		// 4.產生具有HTML 和 純文字的兩種信件內容 EmailBodyContent  並返回
		String htmlContent = templateEngine.process(htmlTemplatePath, context);
		String plainTextContent = templateEngine.process(textTemplatePath, context);
		return new EmailBodyContent(htmlContent, plainTextContent);
	}

	@Override
	public EmailBodyContent generateSpeakerUpdateContent(String speakerName, String adminDashboardUrl) {

		// 1.設置通用變量
		Context context = new Context();
		context.setVariable(FIELD_CONFERENCE_NAME, PROJECT_NAME);
		context.setVariable(FIELD_UPDATE_TIME,
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
		context.setVariable(FIELD_CURRENT_YEAR, String.valueOf(LocalDate.now().getYear()));

		// 2.設置講者更新資料變量
		context.setVariable("speakerName", speakerName);
		context.setVariable("updatedItems", "CV and Profile Photo");
		context.setVariable("adminDashboardUrl", adminDashboardUrl);

		// 3. 這是給管理者的信，所以預設信件就好
		String languagePath = "";

		String htmlTemplatePath = "html" + languagePath + "/speaker-update-notification.html";
		String textTemplatePath = "plain-text" + languagePath + "/speaker-update-notification.txt";

		// 4.產生具有HTML 和 純文字的兩種信件內容 EmailBodyContent  並返回
		String htmlContent = templateEngine.process(htmlTemplatePath, context);
		String plainTextContent = templateEngine.process(textTemplatePath, context);

		return new EmailBodyContent(htmlContent, plainTextContent);
	}

	@Override
	public EmailBodyContent generateWalkInRegistrationContent(Long attendeesId, String bannerPhotoUrl) {
		Context context = new Context();
		// 1.設置通用變量
		context.setVariable(FIELD_BANNER_PHOTO_URL, bannerPhotoUrl);
		context.setVariable(FIELD_CONFERENCE_NAME, PROJECT_NAME);
		context.setVariable(FIELD_DOMAIN, DOMAIN);

		// 2. 這是給管理者的信，所以預設信件就好
		String languagePath = "";

		String htmlTemplatePath = "html" + languagePath + "/walk-in-registration-notification.html";
		String textTemplatePath = "plain-text" + languagePath + "/walk-in-registration-notification.txt";

		// 3.產生具有HTML 和 純文字的兩種信件內容 EmailBodyContent  並返回
		String htmlContent = templateEngine.process(htmlTemplatePath, context);
		String plainTextContent = templateEngine.process(textTemplatePath, context);
		return new EmailBodyContent(htmlContent, plainTextContent);
	}

}
