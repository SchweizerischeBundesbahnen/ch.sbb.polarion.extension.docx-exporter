package ch.sbb.polarion.extension.docx_exporter.pandoc;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParamSetCondition implements ExecutionCondition {

    public static final String DOCKER = "docker";

    private static final Logger logger = LoggerFactory.getLogger(ParamSetCondition.class);

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext extensionContext) {
        String implValue = System.getProperty(BasePandocTest.IMPL_NAME_PARAM);
        if (DOCKER.equalsIgnoreCase(implValue)) {
            return ConditionEvaluationResult.enabled("ok");
        } else {
            logger.info("Param {} doesn't set, skipping pandoc test", BasePandocTest.IMPL_NAME_PARAM);
            return ConditionEvaluationResult.disabled("required param doesn't exist");
        }
    }
}
