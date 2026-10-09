package com.yellow.trade.notifications;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** This module's mappers, scanned here so the module carries its own wiring. */
@Configuration
@MapperScan(basePackageClasses = NotificationMapper.class, annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class NotificationsConfig {
}
