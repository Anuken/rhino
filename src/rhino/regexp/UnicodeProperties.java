package rhino.regexp;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unicode properties handler for Java 11 Character class. Handles binary properties from ECMA-262
 * and general category values.
 */
public class UnicodeProperties {
    // Binary Property Names (from ECMA-262 table-binary-unicode-properties)
    public static final byte ALPHABETIC = 1;
    public static final byte ASCII = ALPHABETIC + 1;
    public static final byte CASE_IGNORABLE = ASCII + 1;
    public static final byte ASCII_HEX_DIGIT = CASE_IGNORABLE + 1;
    public static final byte HEX_DIGIT = ASCII_HEX_DIGIT + 1;
    public static final byte ID_CONTINUE = HEX_DIGIT + 1;
    public static final byte ID_START = ID_CONTINUE + 1;
    public static final byte LOWERCASE = ID_START + 1;
    public static final byte UPPERCASE = LOWERCASE + 1;
    public static final byte WHITE_SPACE = UPPERCASE + 1;

    // Non-binary properties
    public static final byte GENERAL_CATEGORY = WHITE_SPACE + 1;
    public static final byte SCRIPT = GENERAL_CATEGORY + 1;

    // Property Values for General Category (from PropertyValueAliases.txt)
    // OTHER
    public static final byte OTHER = 1;
    public static final byte CONTROL = OTHER + 1;
    public static final byte FORMAT = CONTROL + 1;
    public static final byte UNASSIGNED = FORMAT + 1;
    public static final byte PRIVATE_USE = UNASSIGNED + 1;
    public static final byte SURROGATE = PRIVATE_USE + 1;
    public static final byte LETTER = SURROGATE + 1;
    public static final byte LOWERCASE_LETTER = LETTER + 1;
    public static final byte MODIFIER_LETTER = LOWERCASE_LETTER + 1;
    public static final byte OTHER_LETTER = MODIFIER_LETTER + 1;
    public static final byte TITLECASE_LETTER = OTHER_LETTER + 1;
    public static final byte UPPERCASE_LETTER = TITLECASE_LETTER + 1;
    public static final byte MARK = UPPERCASE_LETTER + 1;
    public static final byte SPACING_MARK = MARK + 1;
    public static final byte ENCLOSING_MARK = SPACING_MARK + 1;
    public static final byte NONSPACING_MARK = ENCLOSING_MARK + 1;
    public static final byte NUMBER = NONSPACING_MARK + 1;
    public static final byte DECIMAL_NUMBER = NUMBER + 1;
    public static final byte LETTER_NUMBER = DECIMAL_NUMBER + 1;
    public static final byte OTHER_NUMBER = LETTER_NUMBER + 1;
    public static final byte PUNCTUATION = OTHER_NUMBER + 1;
    public static final byte CONNECTOR_PUNCTUATION = PUNCTUATION + 1;
    public static final byte DASH_PUNCTUATION = CONNECTOR_PUNCTUATION + 1;
    public static final byte CLOSE_PUNCTUATION = DASH_PUNCTUATION + 1;
    public static final byte FINAL_PUNCTUATION = CLOSE_PUNCTUATION + 1;
    public static final byte INITIAL_PUNCTUATION = FINAL_PUNCTUATION + 1;
    public static final byte OTHER_PUNCTUATION = INITIAL_PUNCTUATION + 1;
    public static final byte OPEN_PUNCTUATION = OTHER_PUNCTUATION + 1;
    public static final byte SYMBOL = OPEN_PUNCTUATION + 1;
    public static final byte CURRENCY_SYMBOL = SYMBOL + 1;
    public static final byte MODIFIER_SYMBOL = CURRENCY_SYMBOL + 1;
    public static final byte MATH_SYMBOL = MODIFIER_SYMBOL + 1;
    public static final byte OTHER_SYMBOL = MATH_SYMBOL + 1;
    public static final byte SEPARATOR = OTHER_SYMBOL + 1;
    public static final byte LINE_SEPARATOR = SEPARATOR + 1;
    public static final byte PARAGRAPH_SEPARATOR = LINE_SEPARATOR + 1;
    public static final byte SPACE_SEPARATOR = PARAGRAPH_SEPARATOR + 1;

    // Binary property values
    public static final byte TRUE = SPACE_SEPARATOR + 1;
    public static final byte FALSE = TRUE + 1;

    // group 1 is the property name, group 2 the optional value
    private static final Pattern PROPERTY_PATTERN =
            Pattern.compile("^([a-zA-Z_]+)(?:=([a-zA-Z_0-9]+))?$");

    // Property Name Map (canonical names and aliases)
    public static final Map<String, Byte> PROPERTY_NAMES =
            byteMap(
                    "Alphabetic", ALPHABETIC,
                    "Alpha", ALPHABETIC,
                    "ASCII", ASCII,
                    "Case_Ignorable", CASE_IGNORABLE,
                    "CI", CASE_IGNORABLE,
                    "General_Category", GENERAL_CATEGORY,
                    "gc", GENERAL_CATEGORY,
                    "Script", SCRIPT,
                    "sc", SCRIPT,
                    "ASCII_Hex_Digit", ASCII_HEX_DIGIT,
                    "AHex", ASCII_HEX_DIGIT,
                    "Hex_Digit", HEX_DIGIT,
                    "Hex", HEX_DIGIT,
                    "ID_Continue", ID_CONTINUE,
                    "IDC", ID_CONTINUE,
                    "ID_Start", ID_START,
                    "IDS", ID_START,
                    "Lowercase", LOWERCASE,
                    "Lower", LOWERCASE,
                    "Uppercase", UPPERCASE,
                    "Upper", UPPERCASE,
                    "White_Space", WHITE_SPACE,
                    "space", WHITE_SPACE);

    // Property Value Map for General Category (canonical names and aliases)
    public static final Map<String, Byte> PROPERTY_VALUES =
            byteMap(
                    "Other", OTHER,
                    "C", OTHER,
                    "Control", CONTROL,
                    "Cc", CONTROL,
                    "cntrl", CONTROL,
                    "Format", FORMAT,
                    "Cf", FORMAT,
                    "Unassigned", UNASSIGNED,
                    "Cn", UNASSIGNED,
                    "Private_Use", PRIVATE_USE,
                    "Co", PRIVATE_USE,
                    "Surrogate", SURROGATE,
                    "Cs", SURROGATE,
                    "Letter", LETTER,
                    "L", LETTER,
                    "Lowercase_Letter", LOWERCASE_LETTER,
                    "Ll", LOWERCASE_LETTER,
                    "Modifier_Letter", MODIFIER_LETTER,
                    "Lm", MODIFIER_LETTER,
                    "Other_Letter", OTHER_LETTER,
                    "Lo", OTHER_LETTER,
                    "Titlecase_Letter", TITLECASE_LETTER,
                    "Lt", TITLECASE_LETTER,
                    "Uppercase_Letter", UPPERCASE_LETTER,
                    "Lu", UPPERCASE_LETTER,
                    "Mark", MARK,
                    "M", MARK,
                    "Combining_Mark", MARK,
                    "Spacing_Mark", SPACING_MARK,
                    "Mc", SPACING_MARK,
                    "Enclosing_Mark", ENCLOSING_MARK,
                    "Me", ENCLOSING_MARK,
                    "Nonspacing_Mark", NONSPACING_MARK,
                    "Mn", NONSPACING_MARK,
                    "Number", NUMBER,
                    "N", NUMBER,
                    "Decimal_Number", DECIMAL_NUMBER,
                    "Nd", DECIMAL_NUMBER,
                    "digit", NUMBER,
                    "Letter_Number", LETTER_NUMBER,
                    "Nl", LETTER_NUMBER,
                    "Other_Number", OTHER_NUMBER,
                    "No", OTHER_NUMBER,
                    "Punctuation", PUNCTUATION,
                    "P", PUNCTUATION,
                    "punct", PUNCTUATION,
                    "Connector_Punctuation", CONNECTOR_PUNCTUATION,
                    "Pc", CONNECTOR_PUNCTUATION,
                    "Dash_Punctuation", DASH_PUNCTUATION,
                    "Pd", DASH_PUNCTUATION,
                    "Close_Punctuation", CLOSE_PUNCTUATION,
                    "Pe", CLOSE_PUNCTUATION,
                    "Final_Punctuation", FINAL_PUNCTUATION,
                    "Pf", FINAL_PUNCTUATION,
                    "Initial_Punctuation", INITIAL_PUNCTUATION,
                    "Pi", INITIAL_PUNCTUATION,
                    "Other_Punctuation", OTHER_PUNCTUATION,
                    "Po", OTHER_PUNCTUATION,
                    "Open_Punctuation", OPEN_PUNCTUATION,
                    "Ps", OPEN_PUNCTUATION,
                    "Symbol", SYMBOL,
                    "S", SYMBOL,
                    "Currency_Symbol", CURRENCY_SYMBOL,
                    "Sc", CURRENCY_SYMBOL,
                    "Modifier_Symbol", MODIFIER_SYMBOL,
                    "Sk", MODIFIER_SYMBOL,
                    "Math_Symbol", MATH_SYMBOL,
                    "Sm", MATH_SYMBOL,
                    "Other_Symbol", OTHER_SYMBOL,
                    "So", OTHER_SYMBOL,
                    "Separator", SEPARATOR,
                    "Z", SEPARATOR,
                    "Line_Separator", LINE_SEPARATOR,
                    "Zl", LINE_SEPARATOR,
                    "Paragraph_Separator", PARAGRAPH_SEPARATOR,
                    "Zp", PARAGRAPH_SEPARATOR,
                    "Space_Separator", SPACE_SEPARATOR,
                    "Zs", SPACE_SEPARATOR);

    private static Map<String, Byte> byteMap(Object... keysAndValues) {
        HashMap<String, Byte> map = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], (Byte) keysAndValues[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * Looks up a property name and optionally a value and returns an encoded int. For binary
     * properties, combines the property name with TRUE. For General_Category, combines
     * General_Category with the specified value.
     *
     * @param propertyOrValue Property name or property name=value pair
     * @return Encoded int combining property name and value
     */
    public static int lookup(String propertyOrValue) {
        if (propertyOrValue == null || propertyOrValue.isEmpty()) {
            return -1;
        }

        Matcher m = PROPERTY_PATTERN.matcher(propertyOrValue);
        m.find();
        if (!m.matches() || m.group(1) == null) {
            return -1;
        }

        if (m.group(2) == null) {
            // It's a single property name (binary property)
            String property = m.group(1);

            Byte propByte = PROPERTY_NAMES.get(property);

            if (propByte == null) {
                // Check if it's a general category value without the gc= prefix
                Byte valueByte = PROPERTY_VALUES.get(property);
                if (valueByte != null) {
                    // It's a GC value, encode it with GC property
                    return encodeProperty(GENERAL_CATEGORY, valueByte);
                }
                return -1;
            }

            if (propByte == GENERAL_CATEGORY || propByte == SCRIPT) {
                return -1;
            }

            // It's a binary property, encode with TRUE
            return encodeProperty(propByte, TRUE);
        } else {
            // It's a property=value format
            String property = m.group(1);
            String value = m.group(2);

            Byte propByte = PROPERTY_NAMES.get(property);
            if (propByte == null) {
                return -1;
            }

            switch (propByte) {
                case GENERAL_CATEGORY:
                    Byte valueByte = PROPERTY_VALUES.get(value);
                    if (valueByte == null) {
                        return -1;
                    }
                    return encodeProperty(GENERAL_CATEGORY, valueByte);
                case SCRIPT:
                    int script = UnicodeScripts.forName(value);
                    return script < 0 ? -1 : encodeProperty(SCRIPT, (byte) script);
                default:
                    // Binary properties don't have values
                    return -1;
            }
        }
    }

    /**
     * Encodes a property name and value into a single int. The property name is in the high 16
     * bits, the value in the low 16 bits.
     *
     * @param property Property name constant
     * @param value Property value constant
     * @return Encoded int
     */
    private static int encodeProperty(byte property, byte value) {
        return ((property & 0xFF) << 8) | (value & 0xFF);
    }

    /**
     * Tests if a code point has a specific Unicode property (direct check without case handling).
     */
    public static boolean hasProperty(int property, int codePoint) {
        byte propByte = (byte) ((property >> 8) & 0xFF);
        int valueByte = (property & 0xFF);
        return hasPropertyDirect(propByte, valueByte, codePoint);
    }

    /** Direct property check without any case handling. */
    private static boolean hasPropertyDirect(byte propByte, int valueByte, int codePoint) {
        switch (propByte) {
            case ALPHABETIC:
                return Character.isAlphabetic(codePoint) == (valueByte == TRUE);

            case ASCII:
                return (codePoint <= 0x7F) == (valueByte == TRUE);

            case CASE_IGNORABLE:
                // Java doesn't have a direct method for this
                // This is an approximation
                return (Character.getType(codePoint) == Character.MODIFIER_SYMBOL
                                || Character.getType(codePoint) == Character.MODIFIER_LETTER
                                || Character.getType(codePoint) == Character.NON_SPACING_MARK)
                        == (valueByte == TRUE);

            case GENERAL_CATEGORY:
                int javaCategory = Character.getType(codePoint);
                return checkGeneralCategory(valueByte, javaCategory);
            case ASCII_HEX_DIGIT:
                return isHexDigit(codePoint) == (valueByte == TRUE);
            case HEX_DIGIT:
                return (Character.digit(codePoint, 16) != -1) == (valueByte == TRUE);
            case ID_CONTINUE:
                return Character.isUnicodeIdentifierPart(codePoint) == (valueByte == TRUE);

            case ID_START:
                return Character.isUnicodeIdentifierStart(codePoint) == (valueByte == TRUE);

            case LOWERCASE:
                return Character.isLowerCase(codePoint) == (valueByte == TRUE);

            case UPPERCASE:
                return Character.isUpperCase(codePoint) == (valueByte == TRUE);

            case WHITE_SPACE:
                {
                    // Note: This only a good approximation of the Unicode white space property
                    return (valueByte == TRUE)
                            == (Character.isSpaceChar(codePoint)
                                    || Character.isWhitespace(codePoint));
                }
            case SCRIPT:
                return UnicodeScripts.of(codePoint) == valueByte;
            default:
                return false;
        }
    }

    /** Maps our property value bytes to Java's Character.getType() values. */
    private static boolean checkGeneralCategory(int propertyValueByte, int javaCategory) {
        switch (propertyValueByte) {
            case LETTER:
                return javaCategory == Character.UPPERCASE_LETTER
                        || javaCategory == Character.LOWERCASE_LETTER
                        || javaCategory == Character.TITLECASE_LETTER
                        || javaCategory == Character.MODIFIER_LETTER
                        || javaCategory == Character.OTHER_LETTER;
            case UPPERCASE_LETTER:
                return javaCategory == Character.UPPERCASE_LETTER;
            case LOWERCASE_LETTER:
                return javaCategory == Character.LOWERCASE_LETTER;
            case TITLECASE_LETTER:
                return javaCategory == Character.TITLECASE_LETTER;
            case MODIFIER_LETTER:
                return javaCategory == Character.MODIFIER_LETTER;
            case OTHER_LETTER:
                return javaCategory == Character.OTHER_LETTER;
            case MARK:
                return javaCategory == Character.NON_SPACING_MARK
                        || javaCategory == Character.ENCLOSING_MARK
                        || javaCategory == Character.COMBINING_SPACING_MARK;
            case NONSPACING_MARK:
                return javaCategory == Character.NON_SPACING_MARK;
            case ENCLOSING_MARK:
                return javaCategory == Character.ENCLOSING_MARK;
            case SPACING_MARK:
                return javaCategory == Character.COMBINING_SPACING_MARK;
            case NUMBER:
                return javaCategory == Character.DECIMAL_DIGIT_NUMBER
                        || javaCategory == Character.LETTER_NUMBER
                        || javaCategory == Character.OTHER_NUMBER;
            case DECIMAL_NUMBER:
                return javaCategory == Character.DECIMAL_DIGIT_NUMBER;
            case LETTER_NUMBER:
                return javaCategory == Character.LETTER_NUMBER;
            case OTHER_NUMBER:
                return javaCategory == Character.OTHER_NUMBER;

            case SEPARATOR:
                return javaCategory == Character.SPACE_SEPARATOR
                        || javaCategory == Character.LINE_SEPARATOR
                        || javaCategory == Character.PARAGRAPH_SEPARATOR;
            case SPACE_SEPARATOR:
                return javaCategory == Character.SPACE_SEPARATOR;
            case LINE_SEPARATOR:
                return javaCategory == Character.LINE_SEPARATOR;
            case PARAGRAPH_SEPARATOR:
                return javaCategory == Character.PARAGRAPH_SEPARATOR;

            case OTHER:
                return javaCategory == Character.OTHER_LETTER
                        || javaCategory == Character.OTHER_NUMBER
                        || javaCategory == Character.OTHER_PUNCTUATION
                        || javaCategory == Character.OTHER_SYMBOL;
            case CONTROL:
                return javaCategory == Character.CONTROL;
            case FORMAT:
                return javaCategory == Character.FORMAT;
            case SURROGATE:
                return javaCategory == Character.SURROGATE;
            case PRIVATE_USE:
                return javaCategory == Character.PRIVATE_USE;

            case PUNCTUATION:
                return javaCategory == Character.CONNECTOR_PUNCTUATION
                        || javaCategory == Character.DASH_PUNCTUATION
                        || javaCategory == Character.START_PUNCTUATION
                        || javaCategory == Character.END_PUNCTUATION
                        || javaCategory == Character.OTHER_PUNCTUATION
                        || javaCategory == Character.INITIAL_QUOTE_PUNCTUATION
                        || javaCategory == Character.FINAL_QUOTE_PUNCTUATION;
            case DASH_PUNCTUATION:
                return javaCategory == Character.DASH_PUNCTUATION;
            case OPEN_PUNCTUATION:
                return javaCategory == Character.START_PUNCTUATION;
            case CLOSE_PUNCTUATION:
                return javaCategory == Character.END_PUNCTUATION;
            case CONNECTOR_PUNCTUATION:
                return javaCategory == Character.CONNECTOR_PUNCTUATION;
            case OTHER_PUNCTUATION:
                return javaCategory == Character.OTHER_PUNCTUATION;
            case INITIAL_PUNCTUATION:
                return javaCategory == Character.INITIAL_QUOTE_PUNCTUATION;
            case FINAL_PUNCTUATION:
                return javaCategory == Character.FINAL_QUOTE_PUNCTUATION;

            case SYMBOL:
                return javaCategory == Character.MATH_SYMBOL
                        || javaCategory == Character.CURRENCY_SYMBOL
                        || javaCategory == Character.MODIFIER_SYMBOL
                        || javaCategory == Character.OTHER_SYMBOL;
            case MATH_SYMBOL:
                return javaCategory == Character.MATH_SYMBOL;
            case CURRENCY_SYMBOL:
                return javaCategory == Character.CURRENCY_SYMBOL;
            case MODIFIER_SYMBOL:
                return javaCategory == Character.MODIFIER_SYMBOL;
            case OTHER_SYMBOL:
                return javaCategory == Character.OTHER_SYMBOL;
            case UNASSIGNED:
                return javaCategory == Character.UNASSIGNED;

            default:
                return false;
        }
    }

    /** Checks if a code point is a hex digit. */
    private static boolean isHexDigit(int codePoint) {
        return (codePoint >= '0' && codePoint <= '9')
                || (codePoint >= 'a' && codePoint <= 'f')
                || (codePoint >= 'A' && codePoint <= 'F');
    }
}
