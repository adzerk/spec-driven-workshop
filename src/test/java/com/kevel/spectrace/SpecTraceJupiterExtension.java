package com.kevel.spectrace;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class SpecTraceJupiterExtension implements BeforeEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        SpecTraceRuntime.validateAndRecord(context.getRequiredTestMethod());
    }
}
