package tw.org.dtcss.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 對標 member table , category 屬性
 * 
 */
@Getter
@AllArgsConstructor
public enum MemberCategoryEnum {
	MEMBER(1,"member", "8/22 Physician", "8/22 醫師場"),
	OTHERS(2,"others", "8/22 TzuChi Nurse", "8/23 護理場_慈濟體系專區"),
	NON_MEMBER(3,"non-member", "8/22 Nurse", "8/23 護理場"),
	MVP(4,"mvp", "MVP", "MVP"),
	SPEAKER(5,"speaker", "Speaker", "講者"),
	MODERATOR(6,"moderator", "Moderator", "座長"),
	STAFF(7,"staff" ,"Staff", "工作人員");

	private final Integer value;
	private final String configKey;
	private final String labelEn;
	private final String labelZh;

	public static MemberCategoryEnum fromValue(Integer value) {
		for (MemberCategoryEnum type : values()) {
			if (type.value.equals(value))
				return type;
		}
		throw new IllegalArgumentException("無效的會員身份值: " + value);
	}

}
