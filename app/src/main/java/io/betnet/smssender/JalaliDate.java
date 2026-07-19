package io.betnet.smssender;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class JalaliDate {
    private JalaliDate() {}

    public static String format(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTime(new Date(millis));
        int gy = c.get(Calendar.YEAR);
        int gm = c.get(Calendar.MONTH) + 1;
        int gd = c.get(Calendar.DAY_OF_MONTH);
        int[] j = gregorianToJalali(gy, gm, gd);
        return String.format(Locale.US, "%04d/%02d/%02d %02d:%02d:%02d", j[0], j[1], j[2],
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND));
    }

    private static int[] gregorianToJalali(int gy, int gm, int gd) {
        int[] gdm = {0,31,59,90,120,151,181,212,243,273,304,334};
        int gy2 = gm > 2 ? gy + 1 : gy;
        int days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + gdm[gm - 1];
        int jy = -1595 + 33 * (days / 12053);
        days %= 12053;
        jy += 4 * (days / 1461);
        days %= 1461;
        if (days > 365) {
            jy += (days - 1) / 365;
            days = (days - 1) % 365;
        }
        int jm, jd;
        if (days < 186) { jm = 1 + days / 31; jd = 1 + days % 31; }
        else { jm = 7 + (days - 186) / 30; jd = 1 + (days - 186) % 30; }
        return new int[]{jy, jm, jd};
    }
}
