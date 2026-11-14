package com.riseai.service;

import com.riseai.dto.response.ChainActivityResponse;

public interface ChainActivityService {
    ChainActivityResponse activity(String pageToken);
}
