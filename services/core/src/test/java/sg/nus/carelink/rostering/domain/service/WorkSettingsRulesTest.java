package sg.nus.carelink.rostering.domain.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import sg.nus.carelink.rostering.domain.model.CaregiverAvailability.Status;
import sg.nus.carelink.rostering.domain.model.CaregiverPreference;
import sg.nus.carelink.rostering.domain.model.CaregiverWorkSettings.*;
import static org.assertj.core.api.Assertions.*;

class WorkSettingsRulesTest {
    private final Catalog catalog=new Catalog(List.of("PERSONAL_CARE"),List.of("01","North"),8,new BigDecimal("8.0"),true);
    private final LocalDate today=LocalDate.of(2026,10,12);
    private Window window(String start,String end,Status status) { return new Window(LocalTime.parse(start),LocalTime.parse(end),status); }
    private Preferences preferences(List<WeeklyWindow> windows) { return new Preferences(List.of(),List.of(),windows,null,null); }
    @Test void adjacentWindowsAreSortedAndAllowedButOppositeStatusesCannotOverlap() {
        var first=window("09:00","12:00",Status.AVAILABLE); var next=window("12:00","13:00",Status.UNAVAILABLE);
        assertThat(WorkSettingsRules.windows(List.of(next,first))).containsExactly(first,next);
        assertThatThrownBy(()->WorkSettingsRules.windows(List.of(first,window("11:59","13:00",Status.UNAVAILABLE)))).hasMessageContaining("overlap");
    }
    @Test void emptyDayMeansNoWindowsNotAnAllDayUnavailableWindow() {
        assertThat(WorkSettingsRules.windows(List.of())).isEmpty();
        assertThatThrownBy(()->WorkSettingsRules.windows(Collections.nCopies(9,window("09:00","10:00",Status.AVAILABLE)))) .hasMessageContaining("eight");
        assertThatThrownBy(()->WorkSettingsRules.windows(Arrays.asList((Window)null))).hasMessageContaining("eight");
    }
    @Test void invalidTimesAndStatusAreRejected() {
        for(var w:List.of(window("23:00","01:00",Status.AVAILABLE),window("09:00","09:00",Status.AVAILABLE),window("09:00:01","10:00",Status.AVAILABLE),window("09:00","10:00",null)))
            assertThatThrownBy(()->WorkSettingsRules.windows(List.of(w))).isInstanceOf(sg.nus.carelink.shared.error.BusinessRuleViolation.class);
    }
    @Test void calendarLimitsIncludeTheirEndpoints() {
        WorkSettingsRules.editableDate(today,today); WorkSettingsRules.editableDate(today.plusDays(90),today);
        WorkSettingsRules.range(today,today.plusDays(30));
        assertThatThrownBy(()->WorkSettingsRules.editableDate(today.minusDays(1),today)).hasMessageContaining("90");
        assertThatThrownBy(()->WorkSettingsRules.editableDate(today.plusDays(91),today)).hasMessageContaining("90");
        assertThatThrownBy(()->WorkSettingsRules.range(today,today.plusDays(31))).hasMessageContaining("31");
        assertThatThrownBy(()->WorkSettingsRules.range(today,today.minusDays(1))).hasMessageContaining("31");
    }
    @Test void codesAreCanonicalAndPreserveLeadingZeroes() {
        var result=WorkSettingsRules.preferences(new Preferences(List.of("PERSONAL_CARE","PERSONAL_CARE"),List.of("North","01"),List.of(),4,new BigDecimal("4.5")),catalog);
        assertThat(result.serviceCodes()).containsExactly("PERSONAL_CARE"); assertThat(result.sectors()).containsExactly("01","North");
        assertThat(WorkSettingsRules.csv(result.sectors())).isEqualTo("01,North");
        assertThat(WorkSettingsRules.csv(List.of())).isNull();
        assertThatThrownBy(()->WorkSettingsRules.preferences(new Preferences(List.of("unknown"),List.of(),List.of(),null,null),catalog)).hasMessageContaining("directory");
    }
    @Test void weeklyWindowsHaveTwoPerDayAndDoNotOverlap() {
        var a=new WeeklyWindow(DayOfWeek.MONDAY,LocalTime.of(8,0),LocalTime.of(12,0));
        var b=new WeeklyWindow(DayOfWeek.MONDAY,LocalTime.of(12,0),LocalTime.of(14,0));
        assertThat(WorkSettingsRules.weeklyCsv(WorkSettingsRules.preferences(preferences(List.of(b,a)),catalog).weeklyWindows())).isEqualTo("MON 08:00-12:00;MON 12:00-14:00");
        assertThatThrownBy(()->WorkSettingsRules.preferences(preferences(List.of(a,a)),catalog)).hasMessageContaining("overlap");
        assertThatThrownBy(()->WorkSettingsRules.preferences(preferences(List.of(a,a,a)),catalog)).hasMessageContaining("two");
        assertThatThrownBy(()->WorkSettingsRules.preferences(preferences(Collections.nCopies(15,a)),catalog)).hasMessageContaining("14");
    }
    @Test void weeklyWindowsAreGroupedByWeekdayBeforeOverlapValidation() {
        var monday=new WeeklyWindow(DayOfWeek.MONDAY,LocalTime.parse("09:30"),LocalTime.parse("11:30"));
        var tuesday=new WeeklyWindow(DayOfWeek.TUESDAY,LocalTime.parse("09:30"),LocalTime.parse("11:30"));
        var allowed=preferences(List.of(tuesday,monday));
        assertThat(WorkSettingsRules.preferences(allowed,catalog).weeklyWindows()).containsExactly(monday,tuesday);
        var overlappingMonday=new WeeklyWindow(DayOfWeek.valueOf("MONDAY"),LocalTime.parse("10:30"),LocalTime.parse("12:30"));
        var overlap=preferences(List.of(tuesday,monday,overlappingMonday));
        assertThatThrownBy(()->WorkSettingsRules.preferences(overlap,catalog)).hasMessageContaining("overlap");
    }
    @Test void numericLimitsAndHalfHoursAreValidated() {
        for(var hours:List.of("0","0.4","4.1","8.5")) assertThatThrownBy(()->WorkSettingsRules.preferences(new Preferences(List.of(),List.of(),List.of(),null,new BigDecimal(hours)),catalog)).hasMessageContaining("half-hour");
        for(int count:List.of(0,9)) assertThatThrownBy(()->WorkSettingsRules.preferences(new Preferences(List.of(),List.of(),List.of(),count,null),catalog)).hasMessageContaining("visit");
        assertThat(WorkSettingsRules.preferences(Preferences.empty(),catalog)).isEqualTo(Preferences.empty());
    }
    @Test void legacyUnknownValuesAreRetainedAndFlaggedRatherThanSilentlyCleared() {
        var issues=new ArrayList<String>();
        var raw=new CaregiverPreference(1L,1L,"UNKNOWN","01","not-a-window;MON 08:00-12:00",2,new BigDecimal("2.0"),LocalDateTime.now());
        var result=WorkSettingsRules.read(raw,catalog,issues);
        assertThat(result.serviceCodes()).containsExactly("UNKNOWN"); assertThat(result.weeklyWindows()).hasSize(1);
        assertThat(issues).contains("LEGACY_WEEKLY_WINDOW_INVALID","LEGACY_PREFERENCES_REQUIRE_REVIEW");
        assertThat(WorkSettingsRules.read(null,catalog,new ArrayList<>())).isEqualTo(Preferences.empty());
    }
    @Test void storageLengthIsValidatedBeforeWriting() {
        var codes=List.of("A".repeat(130),"B".repeat(130));
        assertThatThrownBy(()->WorkSettingsRules.preferences(new Preferences(codes,List.of(),List.of(),null,null),new Catalog(codes,List.of(),8,new BigDecimal("8"),true))).hasMessageContaining("255");
    }
}
