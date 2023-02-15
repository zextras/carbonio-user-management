package com.zextras.carbonio.user_management.core.annotations;

import com.zextras.carbonio.user_management.core.extensions.EbeanTestExtension;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@ExtendWith(EbeanTestExtension.class)
public @interface UnitTest { }