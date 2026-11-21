package com.mirakinetix.callsmith.application.testing;

import java.util.List;

public interface AssertionRepository {
    List<ResponseAssertion> find(String requestId);
    void replace(String requestId, List<ResponseAssertion> assertions);
}
