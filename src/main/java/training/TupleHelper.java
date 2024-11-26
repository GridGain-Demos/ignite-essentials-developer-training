package training;

import org.apache.ignite.table.Tuple;

import java.math.BigDecimal;
import java.util.HashMap;

public class TupleHelper {
    public static HashMap<Integer, BigDecimal> tupleToHashMap(Tuple in) {
        var results = new HashMap<Integer, BigDecimal>();
        for (var c = 0; c < in.columnCount(); c++) {
            var key = Integer.valueOf(in.columnName(c).replaceAll("\"", ""));
            var value = in.<BigDecimal>value(c);
            results.put(key, value);
        }
        return results;
    }

    public static Tuple hashMapToTuple(HashMap<Integer, BigDecimal> in) {
        var results = Tuple.create();
        for (var p : in.entrySet()) {
            results.set(p.getKey().toString(), p.getValue());
        }
        return results;
    }
}
