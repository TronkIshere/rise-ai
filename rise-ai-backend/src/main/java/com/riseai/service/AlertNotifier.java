package com.riseai.service;

import java.util.Map;

public interface AlertNotifier {
    void alert(String kind, Map<String, String> details);
}
