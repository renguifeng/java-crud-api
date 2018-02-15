package com.tqdev.crudapi.meta.definition;

import org.jooq.DSLContext;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.impl.DefaultDataType;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class ColumnDefinition {

	private boolean pk = false;
	private String type;
	private int length = -1;
	private int precision = -1;
	private int scale = -1;
	private boolean nullable = false;
	private String fk = null;

	public boolean getPk() {
		return pk;
	}

	public void setPk(Boolean pk) {
		this.pk = pk;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public int getLength() {
		return length;
	}

	public void setLength(int length) {
		this.length = length;
	}

	public int getPrecision() {
		return precision;
	}

	public void setPrecision(int precision) {
		this.precision = precision;
	}

	public int getScale() {
		return scale;
	}

	public void setScale(int scale) {
		this.scale = scale;
	}

	public boolean getNullable() {
		return nullable;
	}

	public void setNullable(Boolean nullable) {
		this.nullable = nullable;
	}

	public String getFk() {
		return fk;
	}

	public void setFk(String fk) {
		this.fk = fk;
	}

	// hackety hack
	private void override(DSLContext dsl) {
		if (dsl.dialect() == SQLDialect.H2) {
			if (type.equals("geometry")) {
				type = "nclob";
			}
		}
	}

	public DataType<?> getDataType(DSLContext dsl) {
		override(dsl);
		DataType<?> result = DefaultDataType.getDataType(SQLDialect.DEFAULT, type);
		result = result.identity(pk);
		if (length >= 0) {
			result = result.length(length);
		}
		if (precision >= 0) {
			result = result.precision(precision);
		}
		if (scale >= 0) {
			result = result.scale(scale);
		}
		result = result.nullable(nullable);
		return result;
	}

	public static ColumnDefinition fromValue(Field<?> field) {
		ColumnDefinition definition = new ColumnDefinition();
		DataType<?> dataType = field.getDataType();
		definition.setPk(dataType.identity());
		DataType<?> defaultType = dataType.getSQLDataType();
		if (defaultType == null) {
			defaultType = DefaultDataType.getDefaultDataType(dataType.getTypeName());
		}
		definition.setType(defaultType.getTypeName());
		if (dataType.hasLength()) {
			definition.setLength(dataType.length());
		}
		if (dataType.hasPrecision()) {
			definition.setPrecision(dataType.precision());
		}
		if (dataType.hasScale()) {
			definition.setScale(dataType.scale());
		}
		definition.setNullable(dataType.nullable());
		return definition;
	}

}
