/*
 * Copyright 2016 Crown Copyright
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
package uk.gov.gchq.gaffer.sparkaccumulo.operation.handler.scalardd;

import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Value;
import org.apache.spark.rdd.RDD;
import scala.Tuple2;
import scala.collection.TraversableOnce;
import scala.collection.mutable.ArrayBuffer;
import scala.reflect.ClassTag;
import scala.runtime.AbstractFunction1;
import uk.gov.gchq.gaffer.accumulostore.AccumuloStore;
import uk.gov.gchq.gaffer.accumulostore.key.AccumuloElementConverter;
import uk.gov.gchq.gaffer.accumulostore.key.exception.AccumuloElementConversionException;
import uk.gov.gchq.gaffer.accumulostore.utils.Pair;
import uk.gov.gchq.gaffer.data.element.Element;
import uk.gov.gchq.gaffer.operation.OperationException;
import uk.gov.gchq.gaffer.spark.operation.scalardd.ImportRDDOfElements;
import uk.gov.gchq.gaffer.sparkaccumulo.operation.scalardd.ImportKeyValuePairRDDToAccumulo;
import uk.gov.gchq.gaffer.store.Context;
import uk.gov.gchq.gaffer.store.Store;
import uk.gov.gchq.gaffer.store.operation.handler.OperationHandler;

import java.io.Serializable;


public class ImportRDDOfElementsHandler implements OperationHandler<ImportRDDOfElements, Void> {

    private static final String OUTPUT_PATH = "outputPath";
    private static final ClassTag<Tuple2<Key, Value>> TUPLE2_CLASS_TAG = scala.reflect.ClassTag$.MODULE$.apply(Tuple2.class);

    @Override
    public Void doOperation(final ImportRDDOfElements operation, final Context context, final Store store) throws OperationException {
        doOperation(operation, context, (AccumuloStore) store);
        return null;
    }

    public void doOperation(final ImportRDDOfElements operation, final Context context, final AccumuloStore store) throws OperationException {
        ElementConverterFunction func = new ElementConverterFunction(store.getKeyPackage().getKeyConverter());
        RDD<Tuple2<Key, Value>> rdd = operation.getInput().flatMap(func, TUPLE2_CLASS_TAG);
        ImportKeyValuePairRDDToAccumulo op = new ImportKeyValuePairRDDToAccumulo.Builder().input(rdd).outputPath(OUTPUT_PATH).build();
        store.execute(op, context.getUser());
    }

    private class ElementConverterFunction extends AbstractFunction1<Element, TraversableOnce<Tuple2<Key, Value>>> implements Serializable {

        protected ElementConverterFunction(final AccumuloElementConverter converter) {
            this.converter = converter;
        }

        private AccumuloElementConverter converter;

        @Override
        public TraversableOnce<Tuple2<Key, Value>> apply(final Element element) {
            ArrayBuffer<Tuple2<Key, Value>> buf = new ArrayBuffer<>();
            Pair<Key> keys = new Pair<>();
            Value value = null;
            try {
                keys = converter.getKeysFromElement(element);
                value = converter.getValueFromElement(element);
            } catch (AccumuloElementConversionException e) {
            }
            Key first = keys.getFirst();
            if (first != null) {
                buf.$plus$eq(new Tuple2<>(first, value));
            }
            Key second = keys.getSecond();
            if (second != null) {
                buf.$plus$eq(new Tuple2<>(second, value));
            }
            return buf;
        }
    }

}

