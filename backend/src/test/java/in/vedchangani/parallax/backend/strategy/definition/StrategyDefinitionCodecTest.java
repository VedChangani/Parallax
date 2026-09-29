package in.vedchangani.parallax.backend.strategy.definition;

import in.vedchangani.parallax.engine.indicator.IndicatorSpec;
import in.vedchangani.parallax.engine.indicator.IndicatorType;
import in.vedchangani.parallax.engine.strategy.Condition;
import in.vedchangani.parallax.engine.strategy.Operand;
import in.vedchangani.parallax.engine.strategy.Operator;
import in.vedchangani.parallax.engine.strategy.PositionSizing;
import in.vedchangani.parallax.engine.strategy.StrategyDefinition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyDefinitionCodecTest {

    private final StrategyDefinitionMapper mapper = new StrategyDefinitionMapper();
    private final StrategyDefinitionCodec codec = new StrategyDefinitionCodec(mapper);

    private static final String GOLDEN_JSON =
            "{\"schemaVersion\":1,\"entryCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"indicator\","
                    + "\"indicator\":\"SMA\",\"period\":20},\"operator\":\"GT\",\"right\":{\"type\":\"constant\","
                    + "\"value\":\"50.0\"}},\"exitCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},"
                    + "\"operator\":\"LT\",\"right\":{\"type\":\"indicator\",\"indicator\":\"SMA\",\"period\":20}},"
                    + "\"positionSizing\":{\"type\":\"cashFraction\",\"fraction\":\"0.5\"}}";

    private static final String GOLDEN_SHA256 =
            "a9666ae1df0b70d216caa00b6be1030f5bbc4170eb2880692b1a710ca25d03ed";

    private static StrategyDefinition goldenDefinition() {
        IndicatorSpec sma20 = new IndicatorSpec(IndicatorType.SMA, 20);
        return new StrategyDefinition(
                new Condition.Compare(new Operand.IndicatorRef(sma20), Operator.GT, new Operand.Constant(50)),
                new Condition.Compare(new Operand.Close(), Operator.LT, new Operand.IndicatorRef(sma20)),
                new PositionSizing.CashFraction(new BigDecimal("0.5")));
    }

    @Test
    void goldenVectorMatchesExactly() {
        CanonicalStrategyDefinition encoded = codec.encode(goldenDefinition());

        assertEquals(GOLDEN_JSON, encoded.json());
        assertEquals(GOLDEN_SHA256, encoded.sha256());
        assertEquals(StrategyDefinitionCodec.SCHEMA_VERSION, encoded.schemaVersion());
        assertEquals(364, encoded.json().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }

    @Test
    void encodingTwiceGivesIdenticalTextAndHash() {
        StrategyDefinition def = goldenDefinition();
        CanonicalStrategyDefinition a = codec.encode(def);
        CanonicalStrategyDefinition b = codec.encode(def);

        assertEquals(a.json(), b.json());
        assertEquals(a.sha256(), b.sha256());
    }

    @Test
    void decodeOfEncodeEqualsTheOriginalDefinition() {
        StrategyDefinition original = goldenDefinition();
        CanonicalStrategyDefinition encoded = codec.encode(original);

        StrategyDefinition decoded = codec.decode(encoded.schemaVersion(), encoded.json(), encoded.sha256());

        assertEquals(original, decoded);
    }

    @Test
    void decodeThenEncodeGivesIdenticalCanonicalTextAndHash() {
        StrategyDefinition original = goldenDefinition();
        CanonicalStrategyDefinition encoded = codec.encode(original);

        StrategyDefinition decoded = codec.decode(encoded.schemaVersion(), encoded.json(), encoded.sha256());
        CanonicalStrategyDefinition reEncoded = codec.encode(decoded);

        assertEquals(encoded.json(), reEncoded.json());
        assertEquals(encoded.sha256(), reEncoded.sha256());
    }

    @Test
    void deeplyNestedAllAnyTreeRoundTrips() {
        StrategyDefinition def = new StrategyDefinition(
                new Condition.All(List.of(
                        new Condition.Compare(new Operand.Close(), Operator.GT, new Operand.Constant(1)),
                        new Condition.Any(List.of(
                                new Condition.Compare(
                                        new Operand.IndicatorRef(new IndicatorSpec(IndicatorType.EMA, 5)),
                                        Operator.LT, new Operand.Constant(-2.5)),
                                new Condition.All(List.of(
                                        new Condition.Compare(
                                                new Operand.IndicatorRef(new IndicatorSpec(IndicatorType.RSI, 14)),
                                                Operator.GT, new Operand.Constant(70)),
                                        new Condition.Compare(new Operand.Close(), Operator.GT,
                                                new Operand.Constant(0)))))))),
                new Condition.Compare(new Operand.Close(), Operator.LT, new Operand.Constant(0)),
                new PositionSizing.CashFraction(BigDecimal.ONE));

        CanonicalStrategyDefinition encoded = codec.encode(def);
        StrategyDefinition decoded = codec.decode(encoded.schemaVersion(), encoded.json(), encoded.sha256());

        assertEquals(def, decoded);
        assertEquals(encoded.sha256(), codec.encode(decoded).sha256());
    }

    @Test
    void atrAndRocRoundTripThroughTheCanonicalForm() {
        StrategyDefinition def = new StrategyDefinition(
                new Condition.Compare(
                        new Operand.IndicatorRef(new IndicatorSpec(IndicatorType.ATR, 14)),
                        Operator.GT, new Operand.Constant(2)),
                new Condition.Compare(
                        new Operand.IndicatorRef(new IndicatorSpec(IndicatorType.ROC, 12)),
                        Operator.LT, new Operand.Constant(-5)),
                new PositionSizing.CashFraction(BigDecimal.ONE));

        CanonicalStrategyDefinition encoded = codec.encode(def);

        assertTrue(encoded.json().contains("\"indicator\":\"ATR\",\"period\":14"));
        assertTrue(encoded.json().contains("\"indicator\":\"ROC\",\"period\":12"));
        assertEquals(StrategyDefinitionCodec.SCHEMA_VERSION, encoded.schemaVersion());
        assertEquals(def, codec.decode(encoded.schemaVersion(), encoded.json(), encoded.sha256()));
    }

    @Test
    void constantCanonicalFormsMatchDoubleToString() {
        assertConstantCanonical(0.1, "0.1");
        assertConstantCanonical(1e-300, "1.0E-300");
        assertConstantCanonical(70.0, "70.0");
        assertConstantCanonical(-12.5, "-12.5");
        assertConstantCanonical(Double.MAX_VALUE, Double.toString(Double.MAX_VALUE));
        assertConstantCanonical(Double.MIN_VALUE, "4.9E-324");
        assertConstantCanonical(-0.0, "0.0");
    }

    private void assertConstantCanonical(double value, String expectedToken) {
        StrategyDefinition def = definitionWithConstant(value);
        CanonicalStrategyDefinition encoded = codec.encode(def);
        assertTrue(encoded.json().contains("\"value\":\"" + expectedToken + "\""),
                "expected token " + expectedToken + " in " + encoded.json());
        assertEquals(def, codec.decode(encoded.schemaVersion(), encoded.json(), encoded.sha256()));
    }

    private static StrategyDefinition definitionWithConstant(double value) {
        return new StrategyDefinition(
                new Condition.Compare(new Operand.Close(), Operator.GT, new Operand.Constant(value)),
                new Condition.Compare(new Operand.Close(), Operator.LT, new Operand.Constant(0)),
                new PositionSizing.CashFraction(BigDecimal.ONE));
    }

    @Test
    void fractionCanonicalFormHasNoTrailingZerosOrExponent() {
        StrategyDefinition def = new StrategyDefinition(
                new Condition.Compare(new Operand.Close(), Operator.GT, new Operand.Constant(0)),
                new Condition.Compare(new Operand.Close(), Operator.LT, new Operand.Constant(0)),
                new PositionSizing.CashFraction(new BigDecimal("0.500")));

        String json = codec.encode(def).json();
        assertTrue(json.contains("\"fraction\":\"0.5\""), json);
        assertTrue(json.indexOf('E') < 0 || json.indexOf('E') == json.lastIndexOf("indicator"));
    }

    @Test
    void parseRequestAcceptsTheGoldenTransportShapeWithoutSchemaVersion() {
        String request = GOLDEN_JSON.replaceFirst("\"schemaVersion\":1,", "");
        StrategyDefinitionDto dto = codec.parseRequest(request);
        StrategyDefinition def = mapper.toEngine(dto);
        assertEquals(goldenDefinition(), def);
    }

    @Test
    void parseRequestRejectsSchemaVersionInTransport() {
        assertThrows(MalformedStrategyDefinitionException.class, () -> codec.parseRequest(GOLDEN_JSON));
    }

    @Test
    void parseRequestIgnoresWhitespaceAndKeyOrder() {
        String transport = GOLDEN_JSON.replaceFirst("\"schemaVersion\":1,", "");
        String reordered = "{\n  \"positionSizing\": " + extractField(transport, "positionSizing")
                + ",\n  \"exitCondition\": " + extractField(transport, "exitCondition")
                + ",\n  \"entryCondition\": " + extractField(transport, "entryCondition") + "\n}";

        StrategyDefinition fromCompact = mapper.toEngine(codec.parseRequest(transport));
        StrategyDefinition fromReordered = mapper.toEngine(codec.parseRequest(reordered));

        assertEquals(fromCompact, fromReordered);
        assertEquals(codec.encode(fromCompact).json(), codec.encode(fromReordered).json());
    }

    @Test
    void differentEquivalentSpellingsGiveTheSameCanonicalOutputAfterDecode() {
        String a = "{\"entryCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\","
                + "\"right\":{\"type\":\"constant\",\"value\":\"0.500\"}},\"exitCondition\":{\"type\":\"compare\","
                + "\"left\":{\"type\":\"close\"},\"operator\":\"LT\",\"right\":{\"type\":\"constant\",\"value\":"
                + "\"0\"}},\"positionSizing\":{\"type\":\"cashFraction\",\"fraction\":\"5E-1\"}}";
        String b = "{\"entryCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\","
                + "\"right\":{\"type\":\"constant\",\"value\":\"0.5\"}},\"exitCondition\":{\"type\":\"compare\","
                + "\"left\":{\"type\":\"close\"},\"operator\":\"LT\",\"right\":{\"type\":\"constant\",\"value\":"
                + "\"0\"}},\"positionSizing\":{\"type\":\"cashFraction\",\"fraction\":\"0.5\"}}";

        StrategyDefinition defA = mapper.toEngine(codec.parseRequest(a));
        StrategyDefinition defB = mapper.toEngine(codec.parseRequest(b));

        assertEquals(codec.encode(defA).sha256(), codec.encode(defB).sha256());
    }

    private static String extractField(String json, String field) {
        String marker = "\"" + field + "\":";
        int start = json.indexOf(marker) + marker.length();
        int depth = 0;
        int i = start;
        for (; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) {
                    i++;
                    break;
                }
            }
        }
        return json.substring(start, i);
    }

    private MalformedStrategyDefinitionException assertMalformed(String json, String expectedPath,
                                                                   String expectedCategory) {
        MalformedStrategyDefinitionException e = assertThrows(MalformedStrategyDefinitionException.class,
                () -> codec.parseRequest(json));
        assertEquals(expectedPath, e.path());
        assertTrue(e.getMessage().contains(expectedCategory),
                "expected category \"" + expectedCategory + "\" in message: " + e.getMessage());
        assertFalse(e.getMessage().contains("tools.jackson"), e.getMessage());
        assertFalse(e.getMessage().contains("com.fasterxml.jackson"), e.getMessage());
        assertFalse(e.getMessage().contains("Exception"), e.getMessage());
        return e;
    }

    @Test
    void unknownRootPropertyIsMalformed() {
        String bad = "{\"entryCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\",\"right\":{\"type\":\"close\"}},\"exitCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\",\"right\":{\"type\":\"close\"}},"
                + "\"positionSizing\":{\"type\":\"cashFraction\",\"fraction\":\"1\"},\"bogus\":1}";
        assertMalformed(bad, "bogus", "unknown property");
    }

    @Test
    void unknownNestedPropertyIsMalformed() {
        String bad = "{\"entryCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\","
                + "\"right\":{\"type\":\"close\"},\"bogus\":1},\"exitCondition\":"
                + "{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\",\"right\":{\"type\":\"close\"}},"
                + "\"positionSizing\":{\"type\":\"cashFraction\",\"fraction\":\"1\"}}";
        assertMalformed(bad, "entryCondition.bogus", "unknown property");
    }

    @Test
    void duplicateKeyIsMalformed() {
        String bad = "{\"entryCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\",\"right\":{\"type\":\"close\"}},\"entryCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\",\"right\":{\"type\":\"close\"}},"
                + "\"exitCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\",\"right\":{\"type\":\"close\"}},\"positionSizing\":"
                + "{\"type\":\"cashFraction\",\"fraction\":\"1\"}}";
        assertMalformed(bad, "", "duplicate key");
    }

    @Test
    void missingTypeIsMalformed() {
        String bad = "{\"entryCondition\":{},\"exitCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\",\"right\":{\"type\":\"close\"}},"
                + "\"positionSizing\":{\"type\":\"cashFraction\",\"fraction\":\"1\"}}";
        assertMalformed(bad, "entryCondition", "missing type id");
    }

    @Test
    void unknownTypeIsMalformed() {
        String bad = "{\"entryCondition\":{\"type\":\"nope\"},\"exitCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\",\"right\":{\"type\":\"close\"}},"
                + "\"positionSizing\":{\"type\":\"cashFraction\",\"fraction\":\"1\"}}";
        assertMalformed(bad, "entryCondition", "unknown type");
    }

    @Test
    void nullFieldIsMalformed() {
        String bad = "{\"entryCondition\":null,\"exitCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\",\"right\":{\"type\":\"close\"}},"
                + "\"positionSizing\":{\"type\":\"cashFraction\",\"fraction\":\"1\"}}";
        assertMalformed(bad, "entryCondition", "null property");
    }

    @Test
    void nullListElementIsMalformed() {
        String bad = "{\"entryCondition\":{\"type\":\"all\",\"conditions\":[{\"type\":\"compare\",\"left\":"
                + "{\"type\":\"close\"},\"operator\":\"GT\",\"right\":{\"type\":\"close\"}},null]},"
                + "\"exitCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\",\"right\":{\"type\":\"close\"}},\"positionSizing\":"
                + "{\"type\":\"cashFraction\",\"fraction\":\"1\"}}";
        assertMalformed(bad, "entryCondition.conditions[1]", "null property");
    }

    @Test
    void trailingTokensAreMalformed() {
        String transport = GOLDEN_JSON.replaceFirst("\"schemaVersion\":1,", "");
        String bad = transport + "   {}";
        assertMalformed(bad, "", "trailing tokens");
    }

    @Test
    void numberInPlaceOfStringConstantIsMalformed() {
        String bad = "{\"entryCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\","
                + "\"right\":{\"type\":\"constant\",\"value\":70}},\"exitCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\",\"right\":{\"type\":\"close\"}},"
                + "\"positionSizing\":{\"type\":\"cashFraction\",\"fraction\":\"1\"}}";
        assertMalformed(bad, "entryCondition.right.value", "wrong JSON type");
    }

    @Test
    void numberInPlaceOfStringFractionIsMalformed() {
        String bad = "{\"entryCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\",\"right\":{\"type\":\"close\"}},\"exitCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\",\"right\":{\"type\":\"close\"}},"
                + "\"positionSizing\":{\"type\":\"cashFraction\",\"fraction\":0.5}}";
        assertMalformed(bad, "positionSizing.fraction", "wrong JSON type");
    }

    @Test
    void stringInPlaceOfIntPeriodIsMalformed() {
        String bad = "{\"entryCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"indicator\","
                + "\"indicator\":\"SMA\",\"period\":\"20\"},\"operator\":\"GT\",\"right\":{\"type\":\"close\"}},"
                + "\"exitCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\",\"right\":{\"type\":\"close\"}},\"positionSizing\":"
                + "{\"type\":\"cashFraction\",\"fraction\":\"1\"}}";
        assertMalformed(bad, "entryCondition.left.period", "wrong JSON type");
    }

    @Test
    void floatInPlaceOfIntPeriodIsMalformed() {
        String bad = "{\"entryCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"indicator\","
                + "\"indicator\":\"SMA\",\"period\":20.5},\"operator\":\"GT\",\"right\":{\"type\":\"close\"}},"
                + "\"exitCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\",\"right\":{\"type\":\"close\"}},\"positionSizing\":"
                + "{\"type\":\"cashFraction\",\"fraction\":\"1\"}}";
        assertMalformed(bad, "entryCondition.left.period", "wrong JSON type");
    }

    @Test
    void unknownIndicatorEnumIsMalformed() {
        String bad = "{\"entryCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"indicator\","
                + "\"indicator\":\"WMA\",\"period\":20},\"operator\":\"GT\",\"right\":{\"type\":\"close\"}},"
                + "\"exitCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\",\"right\":{\"type\":\"close\"}},\"positionSizing\":"
                + "{\"type\":\"cashFraction\",\"fraction\":\"1\"}}";
        assertMalformed(bad, "entryCondition.left.indicator", "wrong JSON type");
    }

    @Test
    void caseInsensitiveEnumIsMalformed() {
        String bad = "{\"entryCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"indicator\","
                + "\"indicator\":\"sma\",\"period\":20},\"operator\":\"GT\",\"right\":{\"type\":\"close\"}},"
                + "\"exitCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\",\"right\":{\"type\":\"close\"}},\"positionSizing\":"
                + "{\"type\":\"cashFraction\",\"fraction\":\"1\"}}";
        assertMalformed(bad, "entryCondition.left.indicator", "wrong JSON type");
    }

    @Test
    void missingCreatorPropertyIsMalformed() {
        String bad = "{\"entryCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\"},"
                + "\"exitCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\",\"right\":{\"type\":\"close\"}},\"positionSizing\":"
                + "{\"type\":\"cashFraction\",\"fraction\":\"1\"}}";
        assertMalformed(bad, "entryCondition.right", "missing property");
    }

    @Test
    void malformedJsonSyntaxGetsTheGenericCategory() {
        assertMalformed("{not valid json", "", "malformed JSON");
    }

    @Test
    void rootJsonNullIsMalformedNotNpe() {
        assertMalformed("null", "", "wrong JSON type");
    }

    @Test
    void emptyAllGroupIsInvalidNotMalformed() {
        String json = "{\"entryCondition\":{\"type\":\"all\",\"conditions\":[]},"
                + "\"exitCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\",\"right\":{\"type\":\"close\"}},\"positionSizing\":"
                + "{\"type\":\"cashFraction\",\"fraction\":\"1\"}}";
        StrategyDefinitionDto dto = codec.parseRequest(json);
        assertThrows(InvalidStrategyDefinitionException.class, () -> mapper.toEngine(dto));
    }

    @Test
    void reorderedConditionListGivesDifferentCanonicalOutput() {
        StrategyDefinition a = new StrategyDefinition(
                new Condition.All(List.of(
                        new Condition.Compare(new Operand.Close(), Operator.GT, new Operand.Constant(1)),
                        new Condition.Compare(new Operand.Close(), Operator.GT, new Operand.Constant(2)))),
                new Condition.Compare(new Operand.Close(), Operator.LT, new Operand.Constant(0)),
                new PositionSizing.CashFraction(BigDecimal.ONE));
        StrategyDefinition b = new StrategyDefinition(
                new Condition.All(List.of(
                        new Condition.Compare(new Operand.Close(), Operator.GT, new Operand.Constant(2)),
                        new Condition.Compare(new Operand.Close(), Operator.GT, new Operand.Constant(1)))),
                new Condition.Compare(new Operand.Close(), Operator.LT, new Operand.Constant(0)),
                new PositionSizing.CashFraction(BigDecimal.ONE));

        assertTrue(!codec.encode(a).json().equals(codec.encode(b).json()));
        assertTrue(!codec.encode(a).sha256().equals(codec.encode(b).sha256()));
    }

    @Test
    void unsupportedSchemaVersionIsIntegrityFailure() {
        CanonicalStrategyDefinition encoded = codec.encode(goldenDefinition());
        assertThrows(StrategyDefinitionIntegrityException.class,
                () -> codec.decode(2, encoded.json(), encoded.sha256()));
    }

    @Test
    void documentSchemaVersionMismatchIsIntegrityFailure() {
        CanonicalStrategyDefinition encoded = codec.encode(goldenDefinition());
        String documentClaimingVersionZero = encoded.json().replaceFirst("\"schemaVersion\":1,", "\"schemaVersion\":0,");

        assertThrows(StrategyDefinitionIntegrityException.class,
                () -> codec.decode(1, documentClaimingVersionZero, encoded.sha256()));
    }

    @Test
    void malformedStoredJsonIsIntegrityFailure() {
        assertThrows(StrategyDefinitionIntegrityException.class,
                () -> codec.decode(1, "{not valid json", "0".repeat(64)));
    }

    @Test
    void storedJsonNullIsIntegrityFailureNotNpe() {
        assertThrows(StrategyDefinitionIntegrityException.class,
                () -> codec.decode(1, "null", "0".repeat(64)));
    }

    @Test
    void semanticallyInvalidStoredTreeIsIntegrityFailure() {
        String badDocument = "{\"schemaVersion\":1,\"entryCondition\":{\"type\":\"all\",\"conditions\":[]},"
                + "\"exitCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\",\"right\":{\"type\":\"close\"}},\"positionSizing\":"
                + "{\"type\":\"cashFraction\",\"fraction\":\"1\"}}";
        assertThrows(StrategyDefinitionIntegrityException.class,
                () -> codec.decode(1, badDocument, "0".repeat(64)));
    }

    @Test
    void oneFlippedHashCharacterIsIntegrityFailure() {
        CanonicalStrategyDefinition encoded = codec.encode(goldenDefinition());
        char[] chars = encoded.sha256().toCharArray();
        chars[0] = chars[0] == 'a' ? 'b' : 'a';
        String tamperedHash = new String(chars);

        assertThrows(StrategyDefinitionIntegrityException.class,
                () -> codec.decode(encoded.schemaVersion(), encoded.json(), tamperedHash));
    }

    @Test
    void uppercaseHashIsIntegrityFailure() {
        CanonicalStrategyDefinition encoded = codec.encode(goldenDefinition());
        assertThrows(StrategyDefinitionIntegrityException.class,
                () -> codec.decode(encoded.schemaVersion(), encoded.json(), encoded.sha256().toUpperCase()));
    }

    @Test
    void modifiedDocumentWithOriginalHashIsIntegrityFailure() {
        CanonicalStrategyDefinition encoded = codec.encode(goldenDefinition());
        String tamperedJson = encoded.json().replace("\"period\":20", "\"period\":21");

        assertThrows(StrategyDefinitionIntegrityException.class,
                () -> codec.decode(encoded.schemaVersion(), tamperedJson, encoded.sha256()));
    }

    @Test
    void jsonbStyleReformattedStoredTextStillVerifies() {
        CanonicalStrategyDefinition encoded = codec.encode(goldenDefinition());
        String reformatted = "{\n \"schemaVersion\" : 1,\n \"positionSizing\": "
                + extractField(encoded.json(), "positionSizing")
                + ",\n \"exitCondition\": " + extractField(encoded.json(), "exitCondition")
                + ",\n \"entryCondition\": " + extractField(encoded.json(), "entryCondition") + "\n}";

        StrategyDefinition decoded = codec.decode(encoded.schemaVersion(), reformatted, encoded.sha256());
        assertEquals(goldenDefinition(), decoded);
    }

    @Test
    void nullArgumentsThrowNpe() {
        assertThrows(NullPointerException.class, () -> codec.encode(null));
        assertThrows(NullPointerException.class, () -> codec.parseRequest(null));
        assertThrows(NullPointerException.class, () -> codec.decode(1, null, "x"));
        assertThrows(NullPointerException.class, () -> codec.decode(1, "{}", null));
    }

    private record Envelope(String name, StrategyDefinitionDto definition) {
    }

    @Test
    void singleArgumentParseRequestStillDelegatesToTheGenericOverloadUnchanged() {
        String transport = GOLDEN_JSON.replaceFirst("\"schemaVersion\":1,", "");
        assertEquals(codec.parseRequest(transport, StrategyDefinitionDto.class), codec.parseRequest(transport));
    }

    @Test
    void parseRequestWithTypeParsesAnEnvelopeContainingANestedDefinition() {
        String transport = GOLDEN_JSON.replaceFirst("\"schemaVersion\":1,", "");
        String json = "{\"name\":\"my-strategy\",\"definition\":" + transport + "}";

        Envelope envelope = codec.parseRequest(json, Envelope.class);

        assertEquals("my-strategy", envelope.name());
        assertEquals(goldenDefinition(), mapper.toEngine(envelope.definition()));
    }

    @Test
    void parseRequestWithTypeAppliesTheSameStrictRulesToTheNestedDefinition() {
        String transport = GOLDEN_JSON.replaceFirst("\"schemaVersion\":1,", "");
        String badNestedDefinition = transport.replaceFirst("\"period\":20", "\"period\":20,\"bogus\":1");
        String json = "{\"name\":\"my-strategy\",\"definition\":" + badNestedDefinition + "}";

        MalformedStrategyDefinitionException e = assertThrows(MalformedStrategyDefinitionException.class,
                () -> codec.parseRequest(json, Envelope.class));
        assertEquals("definition.entryCondition.left.bogus", e.path());
    }

    @Test
    void parseRequestWithTypeStillRejectsSchemaVersionInsideTheNestedDefinition() {
        String json = "{\"name\":\"my-strategy\",\"definition\":" + GOLDEN_JSON + "}";
        assertThrows(MalformedStrategyDefinitionException.class, () -> codec.parseRequest(json, Envelope.class));
    }

    @Test
    void parseRequestWithTypeRejectsAnUnknownEnvelopeField() {
        String transport = GOLDEN_JSON.replaceFirst("\"schemaVersion\":1,", "");
        String json = "{\"name\":\"my-strategy\",\"definition\":" + transport + ",\"ownerId\":1}";

        MalformedStrategyDefinitionException e = assertThrows(MalformedStrategyDefinitionException.class,
                () -> codec.parseRequest(json, Envelope.class));
        assertEquals("ownerId", e.path());
    }

    @Test
    void parseRequestWithTypeRejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> codec.parseRequest(null, Envelope.class));
        assertThrows(NullPointerException.class, () -> codec.parseRequest("{}", null));
    }
}
