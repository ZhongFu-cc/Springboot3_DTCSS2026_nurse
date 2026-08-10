package tw.org.dtcss.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 自定義表單狀態
 */
@Getter
@AllArgsConstructor
public enum FormFieldTypeEnum {

	TEXT("text", "簡答", true), TEXTAREA("textarea", "詳答", true), NUMBER("number", "數字", true),
	EMAIL("email", "E-Mail", true), SELECT("select", "下拉式選單", true), RADIO("radio", "單選題", true),
	RATE("rate", "評分題", true), CHECKBOX("checkbox", "多選題", true), DATE("date", "日期題", true),
	SECTION("section", "非問題,區塊段落", false),DESCRIPTION("description", "非問題,區塊段落", false), IMAGE("image", "非問題,區塊圖片", false);

	@EnumValue
	@JsonValue // JSON 輸出用
	private final String value;

	private final String label;

	private final Boolean exportable;

	/**
	 * 從value獲取 Enum
	 * 
	 * @param value
	 * @return
	 */
	@JsonCreator // JSON 輸入用
	public static FormFieldTypeEnum fromValue(String value) {
		for (FormFieldTypeEnum formStatus : values()) {
			if (formStatus.value.equals(value))
				return formStatus;
		}
		throw new IllegalArgumentException("無效的表單欄位類型: " + value);
	}

	/**
	 * 從label獲取 Enum
	 * 
	 * @param label
	 * @return
	 */
	public static FormFieldTypeEnum fromLabel(String label) {
		for (FormFieldTypeEnum formStatus : values()) {
			if (formStatus.label.equals(label))
				return formStatus;
		}
		throw new IllegalArgumentException("無效的表單欄位類型: " + label);
	}

	
	public boolean isExportable() {
		return exportable;
	}

}
