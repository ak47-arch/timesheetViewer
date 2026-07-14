package com.timesheet.validator.model;

import java.util.Objects;

/**
 * Represents the location of a value inside an Excel sheet.
 *
 * The parser creates these objects while reading Excel.
 * Validators simply reuse them when creating ValidationIssue entries.
 */
public final class CellReference {

    private final int row;
    private final int column;
    private final String fieldName;

    public CellReference(
            int row,
            int column,
            String fieldName) {

        this.row = row;
        this.column = column;
        this.fieldName = fieldName;
    }

    public int getRow() {
        return row;
    }

    public int getColumn() {
        return column;
    }

    public String getFieldName() {
        return fieldName;
    }

    @Override
    public boolean equals(Object o) {

        if (this == o) {
            return true;
        }

        if (!(o instanceof CellReference)) {
            return false;
        }

        CellReference that = (CellReference) o;

        return row == that.row
                && column == that.column
                && Objects.equals(fieldName, that.fieldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(row, column, fieldName);
    }
}