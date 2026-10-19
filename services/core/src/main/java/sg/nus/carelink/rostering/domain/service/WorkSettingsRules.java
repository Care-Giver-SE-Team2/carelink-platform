package sg.nus.carelink.rostering.domain.service;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import sg.nus.carelink.rostering.domain.model.CaregiverPreference;
import sg.nus.carelink.rostering.domain.model.CaregiverWorkSettings.*;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

/** Canonical structured input and explicit reporting of invalid legacy CSV. */
public final class WorkSettingsRules {
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("HH:mm");
    private WorkSettingsRules() { }
    public static void range(LocalDate from, LocalDate to) {
        if(from==null || to==null || to.isBefore(from) || to.isAfter(from.plusDays(30)))
            throw invalid("Choose a date range of at most 31 days.");
    }
    public static void editableDate(LocalDate date, LocalDate today) {
        if(date==null || date.isBefore(today) || date.isAfter(today.plusDays(90)))
            throw invalid("Choose today through 90 days ahead in Singapore time.");
    }
    public static List<Window> windows(List<Window> input) {
        if(input==null || input.size()>8 || input.stream().anyMatch(Objects::isNull)) throw invalid("Use at most eight windows per day.");
        for(var w:input) { time(w.start(),w.end()); if(w.status()==null) throw invalid("Choose Available or Unavailable."); }
        var sorted=input.stream().sorted(Comparator.comparing(Window::start)).toList();
        for(int i=1;i<sorted.size();i++) if(sorted.get(i).start().isBefore(sorted.get(i-1).end())) throw invalid("Time windows must not overlap.");
        return sorted;
    }
    public static Preferences preferences(Preferences input, Catalog catalog) {
        if(input==null) throw invalid("Preferences are required.");
        var services=codes(input.serviceCodes(),catalog.serviceCodes());
        var sectors=codes(input.sectors(),catalog.sectors());
        if(input.weeklyWindows()==null || input.weeklyWindows().size()>14) throw invalid("Use at most 14 weekly windows.");
        for(var w:input.weeklyWindows()) { if(w==null || w.weekday()==null) throw invalid("Choose a weekday."); time(w.start(),w.end()); }
        var weekly=input.weeklyWindows().stream().sorted(Comparator.comparing(WeeklyWindow::weekday).thenComparing(WeeklyWindow::start)).toList();
        for(var day:DayOfWeek.values()) {
            var onDay=weekly.stream().filter(w -> w.weekday().equals(day)).toList();
            if(onDay.size()>2) throw invalid("Use at most two preferred windows per weekday.");
            if(onDay.size()==2 && onDay.get(1).start().isBefore(onDay.get(0).end())) throw invalid("Weekly windows must not overlap.");
        }
        if(input.maxVisitsPerDay()!=null && (input.maxVisitsPerDay()<1 || input.maxVisitsPerDay()>catalog.maxVisitsPerDay()))
            throw invalid("Daily visit preference exceeds the supported range.");
        var hours=input.maxHoursPerDay();
        if(hours!=null && (hours.compareTo(new BigDecimal("0.5"))<0 || hours.compareTo(catalog.maxHoursPerDay())>0 || hours.remainder(new BigDecimal("0.5")).signum()!=0))
            throw invalid("Choose daily hours in half-hour steps within the supported range.");
        var normalized=new Preferences(services,sectors,weekly,input.maxVisitsPerDay(),hours==null ? null : hours.setScale(1));
        column(weeklyCsv(weekly)); return normalized;
    }
    public static String csv(List<String> values) { return values.isEmpty() ? null : String.join(",",values); }
    public static String weeklyCsv(List<WeeklyWindow> values) {
        return values.isEmpty() ? null : values.stream().map(w -> w.weekday().name().substring(0,3)+" "+TIME.format(w.start())+"-"+TIME.format(w.end())).collect(Collectors.joining(";"));
    }
    public static Preferences read(CaregiverPreference raw,Catalog catalog,List<String> issues) {
        if(raw==null) return Preferences.empty();
        var services=parseCodes(raw.preferredServiceTypes()); var sectors=parseCodes(raw.preferredSectors());
        List<WeeklyWindow> weekly=new ArrayList<>();
        if(raw.preferredTimeWindows()!=null && !raw.preferredTimeWindows().isBlank()) {
            for(String value:raw.preferredTimeWindows().split(";",-1)) {
                try {
                    if(!value.matches("[A-Z]{3} [0-2][0-9]:[0-5][0-9]-[0-2][0-9]:[0-5][0-9]")) throw new IllegalArgumentException();
                    var day=Arrays.stream(DayOfWeek.values()).filter(d -> d.name().startsWith(value.substring(0,3))).findFirst().orElseThrow();
                    weekly.add(new WeeklyWindow(day,LocalTime.parse(value.substring(4,9)),LocalTime.parse(value.substring(10))));
                } catch(RuntimeException _) { issues.add("LEGACY_WEEKLY_WINDOW_INVALID"); }
            }
        }
        var result=new Preferences(services,sectors,List.copyOf(weekly),raw.maxVisitsPerDay(),raw.maxHoursPerDay());
        try { preferences(result,catalog); } catch(BusinessRuleViolation _) { issues.add("LEGACY_PREFERENCES_REQUIRE_REVIEW"); }
        return result;
    }
    private static List<String> parseCodes(String raw) { return raw==null || raw.isBlank() ? List.of() : Arrays.stream(raw.split(",",-1)).map(String::strip).toList(); }
    private static List<String> codes(List<String> input,List<String> supported) {
        if(input==null || input.size()>50 || input.stream().anyMatch(v -> v==null || !supported.contains(v))) throw invalid("Select values from the current directory.");
        var sorted=input.stream().distinct().sorted().toList(); column(csv(sorted)); return sorted;
    }
    private static void time(LocalTime start,LocalTime end) {
        if(start==null || end==null || !start.isBefore(end) || start.getSecond()!=0 || end.getSecond()!=0 || start.getNano()!=0 || end.getNano()!=0)
            throw invalid("Use same-day HH:mm windows, with start before end; midnight crossing is not supported.");
    }
    private static void column(String text) { if(text!=null && text.length()>255) throw invalid("The combined selection exceeds the 255-character storage limit."); }
    private static BusinessRuleViolation invalid(String message) { return new BusinessRuleViolation("WORK_SETTINGS_INVALID",message); }
}
