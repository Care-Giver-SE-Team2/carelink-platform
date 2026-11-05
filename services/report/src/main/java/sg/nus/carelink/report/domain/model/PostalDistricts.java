package sg.nus.carelink.report.domain.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Singapore's fixed table of postal districts: the first two digits of a postal code (its sector)
 * name one of 28 districts. A provider is told the district and its area before it accepts a job,
 * never the address.
 */
public final class PostalDistricts {

    /** A Singapore postal code: six digits standing alone, as at the end of an address. */
    private static final Pattern POSTAL_CODE = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");

    private static final Map<String, District> BY_SECTOR = new HashMap<>();

    static {
        add("01", "Raffles Place, Marina", "01", "02", "03", "04", "05", "06");
        add("02", "Tanjong Pagar", "07", "08");
        add("03", "Queenstown, Tiong Bahru", "14", "15", "16");
        add("04", "Telok Blangah, HarbourFront", "09", "10");
        add("05", "Pasir Panjang, Clementi", "11", "12", "13");
        add("06", "High Street, Beach Road", "17");
        add("07", "Middle Road, Golden Mile", "18", "19");
        add("08", "Little India", "20", "21");
        add("09", "Orchard, River Valley", "22", "23");
        add("10", "Bukit Timah, Holland, Tanglin", "24", "25", "26", "27");
        add("11", "Novena, Thomson", "28", "29", "30");
        add("12", "Balestier, Toa Payoh", "31", "32", "33");
        add("13", "Macpherson, Braddell", "34", "35", "36", "37");
        add("14", "Geylang, Eunos", "38", "39", "40", "41");
        add("15", "Katong, Joo Chiat", "42", "43", "44", "45");
        add("16", "Bedok, Upper East Coast", "46", "47", "48");
        add("17", "Loyang, Changi", "49", "50", "81");
        add("18", "Tampines, Pasir Ris", "51", "52");
        add("19", "Hougang, Punggol, Serangoon Gardens", "53", "54", "55", "82");
        add("20", "Ang Mo Kio, Bishan", "56", "57");
        add("21", "Upper Bukit Timah, Ulu Pandan", "58", "59");
        add("22", "Jurong", "60", "61", "62", "63", "64");
        add("23", "Bukit Batok, Bukit Panjang, Choa Chu Kang", "65", "66", "67", "68");
        add("24", "Lim Chu Kang, Tengah", "69", "70", "71");
        add("25", "Kranji, Woodlands", "72", "73");
        add("26", "Upper Thomson, Springleaf", "77", "78");
        add("27", "Yishun, Sembawang", "75", "76");
        add("28", "Seletar", "79", "80");
    }

    private PostalDistricts() {
    }

    /** The district of a postal code, or of the postal code found in an address; empty when there is none. */
    public static Optional<District> of(String postalCodeOrAddress) {
        if (postalCodeOrAddress == null) {
            return Optional.empty();
        }
        Matcher code = POSTAL_CODE.matcher(postalCodeOrAddress);
        String found = null;
        while (code.find()) {
            found = code.group(1); // the last one: an address ends with its postal code
        }
        return found == null ? Optional.empty() : Optional.ofNullable(BY_SECTOR.get(found.substring(0, 2)));
    }

    private static void add(String district, String area, String... sectors) {
        for (String sector : sectors) {
            BY_SECTOR.put(sector, new District(district, area));
        }
    }

    /** A district as two digits, and the area it covers. */
    public record District(String number, String areaName) {
    }
}
