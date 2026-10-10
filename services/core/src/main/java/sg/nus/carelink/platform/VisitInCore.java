package sg.nus.carelink.platform;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.NoneNestedConditions;
import org.springframework.context.annotation.Conditional;

/**
 * On an adapter that calls visit's own classes in core: the adapter is active while visit runs
 * inside core, which is while {@code carelink.visit-api.base-url} is not set. Its twin, the adapter
 * that calls {@code VisitApi}, carries {@link VisitOutsideCore}, so exactly one of the two is active.
 *
 * <p>When visit moves out, every class with this annotation is deleted, and so is this annotation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(VisitInCore.NoVisitApiAddress.class)
public @interface VisitInCore {

	/** The exact opposite of {@link VisitOutsideCore}'s condition. */
	class NoVisitApiAddress extends NoneNestedConditions {

		NoVisitApiAddress() {
			super(ConfigurationPhase.PARSE_CONFIGURATION);
		}

		@ConditionalOnProperty(prefix = "carelink.visit-api", name = "base-url")
		static class VisitApiAddressSet {
		}

	}

}
