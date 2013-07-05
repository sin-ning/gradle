/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.tasks.testing.junit.result

import org.gradle.api.Action
import org.gradle.api.internal.tasks.testing.BuildableTestClassResult
import org.gradle.api.tasks.testing.TestResult
import org.gradle.messaging.remote.internal.PlaceholderException
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class TestResultSerializerTest extends Specification {
    @Rule
    private TestNameTestDirectoryProvider tmp = new TestNameTestDirectoryProvider()
    final TestResultSerializer serializer = new TestResultSerializer(tmp.createDir("results"))

    def "can write and read results"() {
        expect:
        !serializer.hasResults

        when:
        def failure = new RuntimeException("broken")
        def class1 = new BuildableTestClassResult('Class1', 1234)
        class1.with {
            testcase("method1", 200)
            testcase("method2", 300).failure(failure)
        }
        def class2 = new TestClassResult('Class2', 5678)
        def read = serialize([class1, class2])

        then:
        serializer.hasResults
        read.size() == 2
        def readClass1 = read[0]
        readClass1.className == 'Class1'
        readClass1.startTime == 1234
        readClass1.results.size() == 2

        def readMethod1 = readClass1.results[0]
        readMethod1.name == 'method1'
        readMethod1.resultType == TestResult.ResultType.SUCCESS
        readMethod1.duration == 200
        readMethod1.endTime == 200
        readMethod1.exceptions.empty

        def readMethod2 = readClass1.results[1]
        readMethod2.name == 'method2'
        readMethod2.resultType == TestResult.ResultType.FAILURE
        readMethod2.duration == 300
        readMethod2.endTime == 300
        readMethod2.exceptions.size() == 1
        readMethod2.exceptions[0].class == failure.class
        readMethod2.exceptions[0].message == failure.message
        readMethod2.exceptions[0].stackTrace == failure.stackTrace

        def readClass2 = read[1]
        readClass2.className == 'Class2'
        readClass2.startTime == 5678
        readClass2.results.empty

        when:
        serializer.write([])

        then:
        !serializer.hasResults
    }

    def "can write and read exceptions that are not serializable"() {
        def failure = new RuntimeException("broken") {
            final Object field = new Object()
        }
        def class1 = new BuildableTestClassResult("Foo")
        class1.testcase("method1", 200).failure(failure)

        when:
        def read = serialize([class1])

        then:
        read.size() == 1
        def readClass1 = read[0]
        def readMethod1 = readClass1.results[0]
        readMethod1.exceptions.size() == 1
        readMethod1.exceptions[0].class == PlaceholderException.class
        readMethod1.exceptions[0].message == failure.message
        readMethod1.exceptions[0].toString() == failure.toString()
        readMethod1.exceptions[0].stackTrace == failure.stackTrace
    }

    List<TestClassResult> serialize(Collection<TestClassResult> results) {
        serializer.write(results)
        def result = []
        serializer.read({ result << it } as Action)
        return result
    }
}
