package com.paul.mr_paul.blackbot.Contract;

import android.provider.BaseColumns;

public class VariableContract {

    private VariableContract(){}

    public static final class VariableEntry implements BaseColumns {
        public static final String TABLE_NAME = "variable_data";
        public static final String COLUMN_VAR = "variable";
        public static final String COLUMN_VALUE = "value";
        public static final String COLUMN_TIMESTAMP = "timestamp";
    }

}