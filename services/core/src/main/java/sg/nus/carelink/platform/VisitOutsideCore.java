package sg.nus.carelink.platform;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * On an adapter that calls visit through {@code VisitApi}: the adapter is active once
 * {@code carelink.visit-api.base-url} is set, the same setting that makes the {@code VisitApi}
 * client. Its twin, the adapter that calls visit's own classes, carries {@link VisitInCore}.
 *
 * <p>When visit moves out, the setting points at visit, the twins are deleted, and this
 * annotation can go with them.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(prefix = "carelink.visit-api", name = "base-url")
public @interface VisitOutsideCore {

}
