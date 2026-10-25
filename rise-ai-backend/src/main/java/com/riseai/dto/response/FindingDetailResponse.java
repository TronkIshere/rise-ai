package com.riseai.dto.response;

import java.util.List;

public record FindingDetailResponse(FindingResponse finding, List<OccurrenceResponse> occurrences) {
}
