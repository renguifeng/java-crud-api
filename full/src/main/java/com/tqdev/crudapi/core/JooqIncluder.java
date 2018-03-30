package com.tqdev.crudapi.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.ResultQuery;
import org.jooq.impl.DSL;

import com.tqdev.crudapi.core.record.Record;
import com.tqdev.crudapi.meta.reflection.DatabaseReflection;
import com.tqdev.crudapi.meta.reflection.ReflectedTable;

public class JooqIncluder {

	public static void addMandatoryColumns(String tableName, DatabaseReflection tables, Params params) {
		if (!params.containsKey("include") || !params.containsKey("columns")) {
			return;
		}
		for (String includedTableNames : params.get("include")) {
			ReflectedTable t1 = tables.get(tableName);
			for (String includedTableName : includedTableNames.split(",")) {
				ReflectedTable t2 = tables.get(includedTableName);
				if (t2 == null) {
					continue;
				}
				List<Field<Object>> fks1 = t1.getFksTo(t2.getName());
				ReflectedTable t3 = hasAndBelongsToMany(t1, t2, tables);
				if (t3 != null || !fks1.isEmpty()) {
					params.add("mandatory", t2.getName() + "." + t2.getPk().getName());
				}
				for (Field<Object> fk : fks1) {
					params.add("mandatory", t1.getName() + "." + fk.getName());
				}
				List<Field<Object>> fks2 = t2.getFksTo(t1.getName());
				if (t3 != null || !fks1.isEmpty()) {
					params.add("mandatory", t1.getName() + "." + t1.getPk().getName());
				}
				for (Field<Object> fk : fks2) {
					params.add("mandatory", t2.getName() + "." + fk.getName());
				}
				t1 = t2;
			}
		}
	}

	public static void addIncludes(String tableName, Record record, DatabaseReflection tables, Params params,
			DSLContext dsl) {
		ArrayList<Record> records = new ArrayList<>();
		records.add(record);
		addIncludes(tableName, records, tables, params, dsl);
	}

	private static class TreeMap<T> extends LinkedHashMap<T, TreeMap<T>> {

		/**
		 * 
		 */
		private static final long serialVersionUID = 7895438196186643617L;

		public void put(LinkedList<T> path) {
			if (path.isEmpty()) {
				return;
			}
			T key = path.removeFirst();
			TreeMap<T> val = get(key);
			if (val == null) {
				val = new TreeMap<>();
				put(key, val);
			}
			val.put(path);
		}

	}

	private static TreeMap<ReflectedTable> getIncludesAsTreeMap(DatabaseReflection tables, Params params) {
		if (params.containsKey("include")) {
			TreeMap<ReflectedTable> includes = new TreeMap<>();
			for (String includedTableNames : params.get("include")) {
				LinkedList<ReflectedTable> path = new LinkedList<>();
				for (String includedTableName : includedTableNames.split(",")) {
					ReflectedTable t = tables.get(includedTableName);
					if (t != null) {
						path.add(t);
					}
				}
				includes.put(path);
			}
			return includes;
		}
		return null;
	}

	public static void addIncludes(String tableName, ArrayList<Record> records, DatabaseReflection tables,
			Params params, DSLContext dsl) {

		TreeMap<ReflectedTable> includes = getIncludesAsTreeMap(tables, params);

		if (includes != null) {
			addIncludesForTables(tables.get(tableName), includes, records, tables, params, dsl);
		}
	}

	private static ReflectedTable hasAndBelongsToMany(ReflectedTable t1, ReflectedTable t2, DatabaseReflection tables) {
		for (String tableName : tables.tableNames()) {
			ReflectedTable t3 = tables.get(tableName);
			if (!t3.getFksTo(t1.getName()).isEmpty() && !t3.getFksTo(t2.getName()).isEmpty()) {
				return t3;
			}
		}
		return null;
	}

	private static class HabtmValues {
		protected HashMap<Object, ArrayList<Object>> pkValues = new HashMap<>();
		protected HashMap<Object, Object> fkValues = new HashMap<>();
	}

	private static void addIncludesForTables(ReflectedTable t1, TreeMap<ReflectedTable> includes,
			ArrayList<Record> records, DatabaseReflection tables, Params params, DSLContext dsl) {
		if (includes == null) {
			return;
		}

		for (ReflectedTable t2 : includes.keySet()) {

			boolean belongsTo = !t1.getFksTo(t2.getName()).isEmpty();
			boolean hasMany = !t2.getFksTo(t1.getName()).isEmpty();
			ReflectedTable t3 = hasAndBelongsToMany(t1, t2, tables);
			boolean hasAndBelongsToMany = t3 != null;

			ArrayList<Record> newRecords = new ArrayList<>();
			HashMap<Object, Object> fkValues = null;
			HashMap<Object, ArrayList<Object>> pkValues = null;
			HabtmValues habtmValues = null;

			if (belongsTo) {
				fkValues = getFkEmptyValues(t1, t2, records);
				addFkRecords(t2, fkValues, params, dsl, newRecords);
			}
			if (hasMany) {
				pkValues = getPkEmptyValues(t1, records);
				addPkRecords(t1, t2, pkValues, params, dsl, newRecords);
			}
			if (hasAndBelongsToMany) {
				habtmValues = getHabtmEmptyValues(t1, t2, t3, dsl, records);
				addFkRecords(t2, habtmValues.fkValues, params, dsl, newRecords);
			}

			addIncludesForTables(t2, includes.get(t2), newRecords, tables, params, dsl);

			if (fkValues != null) {
				fillFkValues(t2, newRecords, fkValues);
				setFkValues(t1, t2, records, fkValues);
			}
			if (pkValues != null) {
				fillPkValues(t1, t2, newRecords, pkValues);
				setPkValues(t1, t2, records, pkValues);
			}
			if (habtmValues != null) {
				fillFkValues(t2, newRecords, habtmValues.fkValues);
				setHabtmValues(t1, t3, records, habtmValues);
			}
		}
	}

	private static HashMap<Object, Object> getFkEmptyValues(ReflectedTable t1, ReflectedTable t2,
			ArrayList<Record> records) {
		HashMap<Object, Object> fkValues = new HashMap<>();
		List<Field<Object>> fks = t1.getFksTo(t2.getName());
		for (Field<Object> fk : fks) {
			for (Record record : records) {
				Object fkValue = record.get(fk.getName());
				if (fkValue == null) {
					continue;
				}
				fkValues.put(fkValue, null);
			}
		}
		return fkValues;
	}

	private static void addFkRecords(ReflectedTable t2, HashMap<Object, Object> fkValues, Params params, DSLContext dsl,
			ArrayList<Record> records) {
		Field<Object> pk = t2.getPk();
		ArrayList<Field<?>> fields = JooqColumnSelector.columnNames(t2, false, params);
		ResultQuery<org.jooq.Record> query = dsl.select(fields).from(t2).where(pk.in(fkValues.keySet()));
		for (org.jooq.Record record : query.fetch()) {
			records.add(Record.valueOf(record.intoMap()));
		}
	}

	private static void fillFkValues(ReflectedTable t2, ArrayList<Record> fkRecords, HashMap<Object, Object> fkValues) {
		Field<Object> pk = t2.getPk();
		for (Record fkRecord : fkRecords) {
			Object pkValue = fkRecord.get(pk.getName());
			fkValues.put(pkValue, fkRecord);
		}
	}

	private static void setFkValues(ReflectedTable t1, ReflectedTable t2, ArrayList<Record> records,
			HashMap<Object, Object> fkValues) {
		List<Field<Object>> fks = t1.getFksTo(t2.getName());
		for (Field<Object> fk : fks) {
			for (Record record : records) {
				Object key = record.get(fk.getName());
				if (key == null) {
					continue;
				}
				record.put(fk.getName(), fkValues.get(key));
			}
		}
	}

	private static HashMap<Object, ArrayList<Object>> getPkEmptyValues(ReflectedTable t1, ArrayList<Record> records) {
		HashMap<Object, ArrayList<Object>> pkValues = new HashMap<>();
		for (Record record : records) {
			Object key = record.get(t1.getPk().getName());
			pkValues.put(key, new ArrayList<>());
		}
		return pkValues;
	}

	private static void addPkRecords(ReflectedTable t1, ReflectedTable t2, HashMap<Object, ArrayList<Object>> pkValues,
			Params params, DSLContext dsl, ArrayList<Record> records) {
		List<Field<Object>> fks = t2.getFksTo(t1.getName());
		ArrayList<Field<?>> fields = JooqColumnSelector.columnNames(t2, false, params);
		Condition condition = DSL.falseCondition();
		for (Field<Object> fk : fks) {
			condition = condition.or(fk.in(pkValues.keySet()));
		}
		ResultQuery<org.jooq.Record> query = dsl.select(fields).from(t2).where(condition);
		for (org.jooq.Record record : query.fetch()) {
			records.add(Record.valueOf(record.intoMap()));
		}
	}

	private static void fillPkValues(ReflectedTable t1, ReflectedTable t2, ArrayList<Record> pkRecords,
			HashMap<Object, ArrayList<Object>> pkValues) {
		List<Field<Object>> fks = t2.getFksTo(t1.getName());
		for (Field<Object> fk : fks) {
			for (Record pkRecord : pkRecords) {
				Object key = pkRecord.get(fk.getName());
				ArrayList<Object> records = pkValues.get(key);
				if (records != null) {
					records.add(pkRecord);
				}
			}
		}
	}

	private static void setPkValues(ReflectedTable t1, ReflectedTable t2, ArrayList<Record> records,
			HashMap<Object, ArrayList<Object>> pkValues) {
		for (Record record : records) {
			Object key = record.get(t1.getPk().getName());
			record.put(t2.getName(), pkValues.get(key));
		}
	}

	private static HabtmValues getHabtmEmptyValues(ReflectedTable t1, ReflectedTable t2, ReflectedTable t3,
			DSLContext dsl, ArrayList<Record> records) {
		HashMap<Object, ArrayList<Object>> pkValues = getPkEmptyValues(t1, records);
		HashMap<Object, Object> fkValues = new HashMap<>();

		Field<Object> fk1 = t3.getFksTo(t1.getName()).get(0);
		Field<Object> fk2 = t3.getFksTo(t2.getName()).get(0);
		List<Field<?>> fields = Arrays.asList(fk1, fk2);
		Condition condition = fk1.in(pkValues.keySet());
		ResultQuery<org.jooq.Record> query = dsl.select(fields).from(t3).where(condition);
		for (org.jooq.Record record : query.fetch()) {
			Object val1 = record.get(fk1);
			Object val2 = record.get(fk2);
			pkValues.get(val1).add(val2);
			fkValues.put(val2, null);
		}

		HabtmValues habtmValues = new HabtmValues();
		habtmValues.pkValues = pkValues;
		habtmValues.fkValues = fkValues;
		return habtmValues;
	}

	private static void setHabtmValues(ReflectedTable t1, ReflectedTable t3, ArrayList<Record> records,
			HabtmValues habtmValues) {
		for (Record record : records) {
			Object key = record.get(t1.getPk().getName());
			ArrayList<Object> val = new ArrayList<>();
			ArrayList<Object> fks = habtmValues.pkValues.get(key);
			for (Object fk : fks) {
				val.add(habtmValues.fkValues.get(fk));
			}
			record.put(t3.getName(), val);
		}
	}
}