package net.xdclass.service;

import java.util.Map;

public interface ProductService {
    Map<String, Object> page(int page, int size);
}
