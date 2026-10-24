package org.ecom.largecom.domain;


public enum Role {
    owner, admin, staff;

    public String toSql() {
        return name();
    }

    public static Role fromSql(String value) {
        return Role.valueOf(value.toLowerCase());
    }
}
