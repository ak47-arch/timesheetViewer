package com.timesheet.validator.util;

/**
 * Utility methods callable from Thymeleaf templates via
 * T(com.xyzsoft.excelviewer.util.ExcelUtil).colLetter(n)
 */
public class ExcelUtil {

    private ExcelUtil() {}

    /** Convert 0-based column index to Excel letter(s): 0→A, 25→Z, 26→AA */
    public static String colLetter(int col) {
        StringBuilder sb = new StringBuilder();
        col++; // make 1-based
        while (col > 0) {
            int rem = (col - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            col = (col - 1) / 26;
        }
        return sb.toString();
    }
}
