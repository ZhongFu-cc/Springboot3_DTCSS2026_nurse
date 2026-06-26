package tw.org.dtcss.service.impl;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import lombok.RequiredArgsConstructor;
import tw.org.dtcss.convert.FormConvert;
import tw.org.dtcss.enums.CommonStatusEnum;
import tw.org.dtcss.enums.FormStatusEnum;
import tw.org.dtcss.exception.FormException;
import tw.org.dtcss.mapper.FormMapper;
import tw.org.dtcss.pojo.DTO.addEntityDTO.AddFormDTO;
import tw.org.dtcss.pojo.DTO.putEntityDTO.PutFormDTO;
import tw.org.dtcss.pojo.entity.Form;
import tw.org.dtcss.service.FormService;

/**
 * <p>
 * 自定義客制化表單 服务实现类
 * </p>
 *
 * @author Joey
 * @since 2025-12-23
 */
@Service
@RequiredArgsConstructor
public class FormServiceImpl extends ServiceImpl<FormMapper, Form> implements FormService {

	private final FormConvert formConvert;

	@Override
	public Form searchForm(Long formId) {
		return baseMapper.selectById(formId);
	}

	@Override
	public Form getCheckoutForm() {
		LambdaQueryWrapper<Form> queryWrapper = new LambdaQueryWrapper<>();
		// 1 代表有綁定簽退
		queryWrapper.eq(Form::getRequiredForCheckout, 1);
		return baseMapper.selectOne(queryWrapper);
	}

	@Override
	public boolean existCheckoutFormInDB(Form form) {
		LambdaQueryWrapper<Form> queryWrapper = new LambdaQueryWrapper<>();
		// 1 代表有綁定簽退
		queryWrapper.eq(Form::getRequiredForCheckout, 1)
				.ne(form.getFormId() != null, Form::getFormId, form.getFormId());
		Long formCount = baseMapper.selectCount(queryWrapper);

		// 如果已綁定簽退表單數量 >= 1 , 代表存在 , 否則則不存在
		return formCount >= 1 ? true : false;

	}

	@Override
	public boolean existCheckoutForm(Form form) {
		// 只有當 當前表單是否有要綁定簽退表單 以及 簽退表單已是否存在 的情況會返回true
		return CommonStatusEnum.YES.getValue().equals(form.getRequiredForCheckout())
				&& this.existCheckoutFormInDB(form);
	}

	@Override
	public IPage<Form> searchFormPageByQuery(Page<Form> page, FormStatusEnum formStatusEnum, String queryText) {
		LambdaQueryWrapper<Form> queryWrapper = new LambdaQueryWrapper<>();

		// 提前取出Enum的值
		String statusValue = formStatusEnum != null ? formStatusEnum.getValue() : null;

		// 當 formStatusEnum 不為 null 時才加入篩選條件
		// 當 queryText 不為空字串、空格字串、Null 時才加入篩選條件
		queryWrapper.eq(StringUtils.isNotBlank(statusValue), Form::getStatus, statusValue)
				.and(StringUtils.isNotBlank(queryText), wrapper -> wrapper.like(Form::getTitle, queryText));

		return baseMapper.selectPage(page, queryWrapper);

	}

	@Override
	public Form create(AddFormDTO addForm) {

		Form form = formConvert.addDTOToEntity(addForm);

		if (this.existCheckoutForm(form)) {
			// 拋出 表單 異常
			throw new FormException("簽退表單已存在，無法進行新增，請確保簽退表單只有一份");
		}

		baseMapper.insert(form);
		return form;
	}

	@Override
	public void modify(PutFormDTO putFormDTO) {

		Form form = formConvert.putDTOToEntity(putFormDTO);

		if (this.existCheckoutForm(form)) {
			// 拋出 表單 異常
			throw new FormException("簽退表單已存在，無法進行更新，請確保簽退表單只有一份");
		}

		baseMapper.updateById(form);

	}

}
