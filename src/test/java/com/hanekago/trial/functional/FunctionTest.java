package com.hanekago.trial.functional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("関数型プログラミングの基本")
class FunctionTest {
    final List<String> FRIENDS_LIST = Arrays.asList("Brian", "Nate", "Raju", "Sara", "Scott");

    @Test @DisplayName("拡張for文の置き換え")
    void simpleTest() {
        //　従来方式
        final StringBuilder convStringBuff = new StringBuilder();
        for (var friend : FRIENDS_LIST) {
            convStringBuff.append(friend);
        }
        final String convStr = convStringBuff.toString();
        //　関数型
        final StringBuilder funcStringBuff = new StringBuilder();
        FRIENDS_LIST.forEach(funcStringBuff::append);
        final String funcStr = funcStringBuff.toString();
        // then:
        assertEquals(funcStr, convStr);
    }

    @Test @DisplayName("ラムダを関数に隠蔽")
    void replaceToFuncTest() {
        // ラムダをそのまま
        final List<String> convList = FRIENDS_LIST.stream().filter(friend -> friend.startsWith("S")).toList();
        // 関数インターフェース活用
        final Predicate<String> checkIfStartsWithS = name -> name.startsWith("S");
        final List<String> func1List = FRIENDS_LIST.stream().filter(checkIfStartsWithS).toList();
        assertEquals(func1List, convList);
        // 汎用関数化
        final Function<String, Predicate<String>> checkIfStartsWith = letter -> name -> name.startsWith(letter);
        final List<String> func2List = FRIENDS_LIST.stream().filter(checkIfStartsWith.apply("S")).toList();
        assertEquals(func2List, convList);
    }

    @Test @DisplayName("見つからない場合を想定した検索：Optionalの活用例1")
    void notMatchedTest() {
        // given:
        final Function<String, Predicate<String>> checkIfStartsWith = letter -> name -> name.startsWith(letter);
        // Optional型で受け取ることで、見つからなかった場合の処理をシンプルに記述できる
        Optional<String> foundName = FRIENDS_LIST.stream().filter(checkIfStartsWith.apply("A")).findFirst();
        assertFalse(foundName.isPresent());
        assertEquals(foundName.orElse("Not Found"), "Not Found");
    }

    @Test @DisplayName("nullになる可能性のあるリストを安全に処理する：Optionalの活用例2")
    void nullableTest() {
        // 引数で受け取ったリストを一度Optionalに変換してから処理する
        final Function<List<String>, List<String>> safeListFunc =
                nullableList -> Optional.ofNullable(nullableList).orElse(Collections.emptyList()).stream()
                        .map(item -> "item replaced").toList();

        final List<String> passedNullList = safeListFunc.apply(null);
        assertEquals(passedNullList.size(), 0);

        final List<String> passedNonNullList = safeListFunc.apply(FRIENDS_LIST);
        assertEquals(passedNonNullList.size(), FRIENDS_LIST.size());
    }

    @Test @DisplayName("複数要素でのソートの実装")
    void customSortTest() {
        // given:
        record Person(String name, int age) {
        }
        final List<Person> personList = Stream.of(new Person("Sara", 20), new Person("Greg", 30), new Person("John", 20)).toList();
        // comparingを使ってソート(Comparableな属性値への参照を渡す)
        final List<Person> sortedList = personList.stream().sorted(Comparator.comparing(Person::age).thenComparing(Person::name)).toList();
        // 期待するtoStringの結果
        String expectStr = "[Person[name=John, age=20], Person[name=Sara, age=20], Person[name=Greg, age=30]]";
        assertEquals(sortedList.toString(), expectStr);
    }

    @Test @DisplayName("ListからグルーピングされたMapを作成")
    void groupingTest() {
        // given:
        record Person(String name, int age) {
        }
        final List<Person> personList = Stream.of(new Person("Sara", 20), new Person("Scott", 30), new Person("John", 20)).toList();
        // groupingByを使ってグルーピング
        Map<Integer, List<Person>> groupingByAgeMap = personList.stream()
                .collect(Collectors.groupingBy(Person::age, Collectors.mapping(person -> person, toList())));
        // 期待するtoStringの結果
        String expectStr1 = "{20=[Person[name=Sara, age=20], Person[name=John, age=20]], 30=[Person[name=Scott, age=30]]}";
        assertEquals(groupingByAgeMap.toString(), expectStr1);
        // 各イニシャルで最年長だけ抽出
        Map<Character, Optional<Person>> oldestPersonOfEachLetterMap = personList.stream()
                .collect(Collectors.groupingBy(person -> person.name().charAt(0),
                                               Collectors.reducing(BinaryOperator.maxBy(Comparator.comparing(Person::age)))));
        // 期待するtoStringの結果
        String expectStr2 = "{S=Optional[Person[name=Scott, age=30]], J=Optional[Person[name=John, age=20]]}";
        assertEquals(oldestPersonOfEachLetterMap.toString(), expectStr2);
    }

    @Test @DisplayName("関心の分離")
    void delegateModelTest() {
        // given:
        record Asset(Type type, long price) {
            enum Type {BOND, STOCK}
        }
        final List<Asset> assets = Arrays.asList(new Asset(Asset.Type.BOND, 1000), new Asset(Asset.Type.BOND, 2000)
                , new Asset(Asset.Type.STOCK, 3000), new Asset(Asset.Type.STOCK, 4000));
        // 「assetを合計する」処理から「どのassetを対象にするか」を分離（ストラテジを後から注入
        final BiFunction<List<Asset>, Predicate<Asset>, Long> totalAssetPrice =
                (assetList, assetSelector) -> assetList.stream().filter(assetSelector).mapToLong(Asset::price).sum();

        assertEquals(totalAssetPrice.apply(assets, asset -> asset.type() == Asset.Type.BOND), 3000);
        assertEquals(totalAssetPrice.apply(assets, asset -> asset.type() == Asset.Type.STOCK), 7000);
    }

    @Test @DisplayName("フィルターバターン")
    void filterTest() {
        class Camera {
            Function<Color, Color> filter;

            // 可変長引数でフィルターとなる関数を受け取る
            @SafeVarargs public final void setFilters(final Function<Color, Color>... filters) {
                filter = Stream.of(filters).reduce(Function::compose).orElseGet(Function::identity);
            }

            public Camera() {
                setFilters();
            }

            public Color capture(final Color color) {
                final Color processedColor = filter.apply(color);
                // any other processes.
                return processedColor;
            }
        }
        final Camera camera = new Camera();
        final Function<String, String> printCaptured = filterName -> String.format("with %s: %s", filterName, camera.capture(new Color(200, 100, 200)));
        assertEquals(printCaptured.apply("no filter"), "with no filter: java.awt.Color[r=200,g=100,b=200]");

        camera.setFilters(Color::brighter);
        assertEquals(printCaptured.apply("brighter"), "with brighter: java.awt.Color[r=255,g=142,b=255]");

        camera.setFilters(Color::brighter, Color::darker);
        assertEquals(printCaptured.apply("brighter and darker"), "with brighter and darker: java.awt.Color[r=200,g=100,b=200]");
    }

    @ParameterizedTest @DisplayName("高階関数")
    @ValueSource(strings = { "1234567:1,234,567", "12345:12,345", "123:123" })
    void recursiveFunctionTest(String param) {
        final String inputNumber = param.split(":")[0];
        final String expectedStr = param.split(":")[1];
        class Calculator {
            // BiFunctionをFunctionに変換
            static <IN, OUT> Function<IN, OUT> recursiveFunction(BiFunction<Function<IN, OUT>, IN, OUT> biFunc) {
                return input -> biFunc.apply(recursiveFunction(biFunc), input);
            }

            // 再帰関数は関数自身を渡す必要がある
            static final BiFunction<Function<String, String>, String, String> recursiveInsertSeparator = (self, numberString) -> {
                final int length = numberString.length();
                if (length > 3) {
                    final String before = numberString.substring(0, length - 3);
                    final String after = numberString.substring(length - 3);
                    return self.apply(before) + "," + after;
                } else {
                    return numberString;
                }
            };
        }
        assertEquals(Calculator.recursiveFunction(Calculator.recursiveInsertSeparator).apply(inputNumber), expectedStr);
    }
}