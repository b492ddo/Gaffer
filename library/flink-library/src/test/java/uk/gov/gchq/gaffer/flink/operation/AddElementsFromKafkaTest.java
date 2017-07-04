/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.gchq.gaffer.flink.operation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Sets;
import uk.gov.gchq.gaffer.commonutil.JsonAssert;
import uk.gov.gchq.gaffer.exception.SerialisationException;
import uk.gov.gchq.gaffer.operation.Operation;
import uk.gov.gchq.gaffer.operation.OperationTest;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class AddElementsFromKafkaTest extends OperationTest {
    @Override
    protected Class<? extends Operation> getOperationClass() {
        return AddElementsFromKafka.class;
    }

    @Override
    public void shouldSerialiseAndDeserialiseOperation() throws SerialisationException, JsonProcessingException {
        // Given
        final boolean validate = true;
        final boolean skipInvalid = false;
        final int parallelism = 2;
        final String jobName = "jobName";
        final Class<FlinkTest.BasicGenerator> generator = FlinkTest.BasicGenerator.class;
        final String groupId = "groupId";
        final String topic = "topic";
        final String[] servers = {"server1", "server2"};
        final AddElementsFromKafka op = new AddElementsFromKafka.Builder()
                .jobName(jobName)
                .generator(generator)
                .parallelism(parallelism)
                .validate(validate)
                .skipInvalidElements(skipInvalid)
                .groupId(groupId)
                .topic(topic)
                .bootstrapServers(servers)
                .build();

        // When
        final byte[] json = JSON_SERIALISER.serialise(op, true);
        final AddElementsFromKafka deserialisedOp = JSON_SERIALISER.deserialise(json, AddElementsFromKafka.class);

        // Then
        JsonAssert.assertEquals(String.format("{%n" +
                        "  \"class\" : \"uk.gov.gchq.gaffer.flink.operation.AddElementsFromKafka\",%n" +
                        "  \"topic\" : \"topic\",%n" +
                        "  \"groupId\" : \"groupId\",%n" +
                        "  \"bootstrapServers\" : [ \"server1\", \"server2\" ],%n" +
                        "  \"jobName\" : \"jobName\",%n" +
                        "  \"parallelism\" : 2,%n" +
                        "  \"validate\" : true,%n" +
                        "  \"skipInvalidElements\" : false,%n" +
                        "  \"elementGenerator\" : \"uk.gov.gchq.gaffer.flink.operation.FlinkTest$BasicGenerator\"%n" +
                        "}").getBytes(),
                json);
        assertEquals(jobName, deserialisedOp.getJobName());
        assertEquals(generator, deserialisedOp.getElementGenerator());
        assertEquals(parallelism, deserialisedOp.getParallelism());
        assertEquals(validate, deserialisedOp.isValidate());
        assertEquals(skipInvalid, deserialisedOp.isSkipInvalidElements());
        assertEquals(groupId, deserialisedOp.getGroupId());
        assertEquals(topic, deserialisedOp.getTopic());
        assertArrayEquals(servers, deserialisedOp.getBootstrapServers());
    }

    @Override
    public void builderShouldCreatePopulatedOperation() {
        // Given
        final boolean validate = true;
        final boolean skipInvalid = false;
        final int parallelism = 2;
        final String jobName = "jobName";
        final Class<FlinkTest.BasicGenerator> generator = FlinkTest.BasicGenerator.class;
        final String groupId = "groupId";
        final String topic = "topic";
        final String[] servers = {"server1", "server2"};

        // When
        final AddElementsFromKafka op = new AddElementsFromKafka.Builder()
                .jobName(jobName)
                .generator(generator)
                .parallelism(parallelism)
                .validate(validate)
                .skipInvalidElements(skipInvalid)
                .groupId(groupId)
                .topic(topic)
                .bootstrapServers(servers)
                .build();

        // Then
        assertEquals(jobName, op.getJobName());
        assertEquals(generator, op.getElementGenerator());
        assertEquals(parallelism, op.getParallelism());
        assertEquals(validate, op.isValidate());
        assertEquals(skipInvalid, op.isSkipInvalidElements());
        assertEquals(groupId, op.getGroupId());
        assertEquals(topic, op.getTopic());
        assertArrayEquals(servers, op.getBootstrapServers());
    }

    @Override
    protected Set<String> getRequiredFields() {
        return Sets.newHashSet("groupId", "bootstrapServers", "jobName", "elementGenerator");
    }
}
