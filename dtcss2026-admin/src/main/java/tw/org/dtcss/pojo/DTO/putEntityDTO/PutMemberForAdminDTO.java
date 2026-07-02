package tw.org.dtcss.pojo.DTO.putEntityDTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PutMemberForAdminDTO {

	@NotNull
	@Schema(description = "主鍵ID")
	private Long memberId;

	@Schema(description = "同時作為護照號碼 和 台灣身分證字號使用")
	private String idCard;

//	@NotBlank
	@Schema(description = "頭銜 - 前墜詞")
	private String title;

	@Schema(description = "中文姓名，外國人非必填，台灣人必填")
	private String chineseName;

//	@NotBlank
	@Schema(description = "名字, 華人的名在後  , 外國人的名在前")
	private String firstName;

//	@NotBlank
	@Schema(description = "姓氏, 華人的姓氏在前, 外國人的姓氏在後")
	private String lastName;

	@NotBlank
	@Schema(description = "國家")
	private String country;

	@Schema(description = "匯款帳號-後五碼  台灣會員使用")
	private String remitAccountLast5;

	@Schema(description = "會員資格的身份補充")
	private String categoryExtra;

	@Schema(description = "單位(所屬的機構)")
	private String affiliation;

	@Schema(description = "職稱")
	private String jobTitle;

	@Schema(description = "電話號碼,這邊要使用 國碼-號碼")
	private String phone;

	@Schema(description = "收據抬頭統編")
	private String receipt;

	@NotBlank
	@Schema(description = "餐食調查，填寫葷 或 素")
	private String food;

	@Schema(description = "飲食禁忌")
	private String foodTaboo;

	@Schema(description = "備註")
	private String remark;

}
