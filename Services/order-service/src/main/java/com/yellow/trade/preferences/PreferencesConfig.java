package com.yellow.trade.preferences;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** This module's mappers, scanned here so the module carries its own wiring. */
@Configuration
@MapperScan(basePackageClasses = PreferenceMapper.class, annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class PreferencesConfig {
}
