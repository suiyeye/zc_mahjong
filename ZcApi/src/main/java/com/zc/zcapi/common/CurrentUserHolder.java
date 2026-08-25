package com.zc.zcapi.common;

public final class CurrentUserHolder {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private CurrentUserHolder() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser require() {
        CurrentUser user = HOLDER.get();
        if (user == null) {
            throw new IllegalStateException("Current user is unavailable");
        }
        return user;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
