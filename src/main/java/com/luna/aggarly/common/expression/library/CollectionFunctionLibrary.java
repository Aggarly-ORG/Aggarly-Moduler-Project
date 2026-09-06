package com.luna.aggarly.common.expression.library;

import com.luna.aggarly.common.expression.annotation.ExpressionFunction;
import com.luna.aggarly.common.expression.annotation.ExpressionFunctionLibrary;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@ExpressionFunctionLibrary(value = "collection", prefix = "CollUtils")
public class CollectionFunctionLibrary {

    @ExpressionFunction(value = "first", description = "Returns first element of collection/array")
    public Object first(Object collection) {
        if (collection == null) return null;
        if (collection instanceof List<?> list) {
            return list.isEmpty() ? null : list.get(0);
        }
        if (collection instanceof Collection<?> col) {
            return col.isEmpty() ? null : col.iterator().next();
        }
        if (collection instanceof Object[] arr) {
            return arr.length == 0 ? null : arr[0];
        }
        return collection;
    }

    @ExpressionFunction(value = "last", description = "Returns last element of collection/array")
    public Object last(Object collection) {
        if (collection == null) return null;
        if (collection instanceof List<?> list) {
            return list.isEmpty() ? null : list.get(list.size() - 1);
        }
        if (collection instanceof Object[] arr) {
            return arr.length == 0 ? null : arr[arr.length - 1];
        }
        if (collection instanceof Collection<?> col) {
            Object last = null;
            for (Object item : col) last = item;
            return last;
        }
        return collection;
    }

    @ExpressionFunction(value = "size", description = "Returns number of elements in collection, map, array, or string length")
    public int size(Object collection) {
        if (collection == null) return 0;
        if (collection instanceof Collection<?> col) return col.size();
        if (collection instanceof Map<?, ?> map) return map.size();
        if (collection instanceof Object[] arr) return arr.length;
        if (collection instanceof String str) return str.length();
        return 1;
    }

    @ExpressionFunction(value = "join", description = "Joins collection items into a delimited string")
    public String join(Object collection, String delimiter) {
        if (collection == null) return "";
        String delim = (delimiter != null) ? delimiter : ", ";
        if (collection instanceof Collection<?> col) {
            return col.stream().filter(Objects::nonNull).map(String::valueOf).collect(Collectors.joining(delim));
        }
        if (collection instanceof Object[] arr) {
            return Arrays.stream(arr).filter(Objects::nonNull).map(String::valueOf).collect(Collectors.joining(delim));
        }
        return String.valueOf(collection);
    }

    @ExpressionFunction(value = "slice", description = "Extracts sublist from start to end index")
    public List<Object> slice(Object collection, int start, int end) {
        List<Object> list = toList(collection);
        int len = list.size();
        int s = Math.max(0, Math.min(start, len));
        int e = (end <= 0 || end > len) ? len : end;
        return (s <= e) ? new ArrayList<>(list.subList(s, e)) : List.of();
    }

    @ExpressionFunction(value = "reverse", description = "Reverses the order of elements")
    public List<Object> reverse(Object collection) {
        List<Object> list = toList(collection);
        List<Object> reversed = new ArrayList<>(list);
        Collections.reverse(reversed);
        return reversed;
    }

    @ExpressionFunction(value = "unique", description = "Removes duplicate elements preserving order")
    public List<Object> unique(Object collection) {
        List<Object> list = toList(collection);
        return new ArrayList<>(new LinkedHashSet<>(list));
    }

    @ExpressionFunction(value = "contains_element", description = "Checks if collection contains target element")
    public boolean containsElement(Object collection, Object target) {
        if (collection == null || target == null) return false;
        if (collection instanceof Collection<?> col) {
            return col.contains(target) || col.stream().anyMatch(e -> Objects.equals(String.valueOf(e), String.valueOf(target)));
        }
        if (collection instanceof Object[] arr) {
            return Arrays.stream(arr).anyMatch(e -> Objects.equals(String.valueOf(e), String.valueOf(target)));
        }
        return false;
    }

    @ExpressionFunction(value = "sort", description = "Sorts collection elements in natural order")
    public List<Object> sort(Object collection) {
        List<Object> list = toList(collection);
        List<Object> sorted = new ArrayList<>(list);
        sorted.sort((a, b) -> String.valueOf(a).compareTo(String.valueOf(b)));
        return sorted;
    }

    @ExpressionFunction(value = "filter_nulls", description = "Removes null and blank elements from list")
    public List<Object> filterNulls(Object collection) {
        List<Object> list = toList(collection);
        return list.stream()
                .filter(e -> e != null && !(e instanceof String s && s.isBlank()))
                .collect(Collectors.toList());
    }

    @ExpressionFunction(value = "get_index", description = "Retrieves element at index (zero-based)")
    public Object getIndex(Object collection, int index) {
        List<Object> list = toList(collection);
        if (index >= 0 && index < list.size()) {
            return list.get(index);
        }
        return null;
    }

    private List<Object> toList(Object obj) {
        if (obj == null) return List.of();
        if (obj instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Object> l = (List<Object>) list;
            return l;
        }
        if (obj instanceof Collection<?> col) {
            return new ArrayList<>(col);
        }
        if (obj instanceof Object[] arr) {
            return Arrays.asList(arr);
        }
        return List.of(obj);
    }
}
