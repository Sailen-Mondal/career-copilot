package com.careercopilot.discovery;

import java.util.List;

public interface GreenhouseClient {
    List<GreenhouseJob> fetchJobs(String boardToken);
}
