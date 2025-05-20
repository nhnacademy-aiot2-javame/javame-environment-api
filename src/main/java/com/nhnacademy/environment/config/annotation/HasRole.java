package com.nhnacademy.environment.config.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 권한있는 사람만 메소드 실행 가능
 * 권한 : ROLE_ADMIN, ROLE_OWNER, ROLE_USER
 *
 * @HasRole({"ROLE_ADMIN", "ROLE_OWNER"})
 * User는 권한 없음
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HasRole {
    String[] value(); // 허용할 역할들을 지정
}
