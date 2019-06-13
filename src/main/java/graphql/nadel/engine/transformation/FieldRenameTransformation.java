package graphql.nadel.engine.transformation;

import graphql.execution.ExecutionStepInfo;
import graphql.execution.nextgen.FetchedValueAnalysis;
import graphql.execution.nextgen.result.ExecutionResultNode;
import graphql.language.Field;
import graphql.language.SelectionSet;
import graphql.nadel.dsl.FieldMappingDefinition;
import graphql.nadel.engine.ExecutionStepInfoMapper;
import graphql.nadel.engine.FetchedValueAnalysisMapper;
import graphql.nadel.engine.FieldMetadataUtil;
import graphql.nadel.engine.UnapplyEnvironment;
import graphql.util.TraversalControl;

import java.util.List;
import java.util.function.BiFunction;

import static graphql.language.SelectionSet.newSelectionSet;
import static graphql.nadel.engine.transformation.FieldUtils.addFieldIdToChildren;
import static graphql.nadel.engine.transformation.FieldUtils.getSubTree;
import static graphql.nadel.engine.transformation.FieldUtils.pathToFields;
import static graphql.util.TreeTransformerUtil.changeNode;

public class FieldRenameTransformation extends FieldTransformation {

    FetchedValueAnalysisMapper fetchedValueAnalysisMapper = new FetchedValueAnalysisMapper();
    ExecutionStepInfoMapper executionStepInfoMapper = new ExecutionStepInfoMapper();
    private final FieldMappingDefinition mappingDefinition;

    public FieldRenameTransformation(FieldMappingDefinition mappingDefinition) {
        this.mappingDefinition = mappingDefinition;
    }


    @Override
    public FieldMappingDefinition getDefinition() {
        return mappingDefinition;
    }

    @Override
    public TraversalControl apply(ApplyEnvironment environment) {
        super.apply(environment);
        List<String> path = mappingDefinition.getInputPath();
        Field changedNode = environment.getField().transform(builder -> builder.name(mappingDefinition.getInputPath().get(0)));
        changedNode = FieldMetadataUtil.addFieldMetadata(changedNode, getFieldId(), true, false);
        SelectionSet selectionSetWithIds = addFieldIdToChildren(environment.getField(), getFieldId()).getSelectionSet();
        if (path.size() > 1) {
            Field firstChildField = pathToFields(path.subList(1, path.size()), getFieldId(), false, selectionSetWithIds);
            changedNode = changedNode.transform(builder -> builder.selectionSet(newSelectionSet().selection(firstChildField).build()));
        } else {
            changedNode = changedNode.transform(builder -> builder.selectionSet(selectionSetWithIds));
        }
        return changeNode(environment.getTraverserContext(), changedNode);

    }


    @Override
    public UnapplyResult unapplyResultNode(ExecutionResultNode executionResultNode,
                                           List<FieldTransformation> allTransformations,
                                           UnapplyEnvironment environment) {
        ExecutionResultNode subTree = getSubTree(executionResultNode, mappingDefinition.getInputPath().size() - 1);
        FetchedValueAnalysis fetchedValueAnalysis = subTree.getFetchedValueAnalysis();

        BiFunction<ExecutionStepInfo, UnapplyEnvironment, ExecutionStepInfo> esiMapper = (esi, env) -> {
            ExecutionStepInfo esiWithMappedField = replaceFieldsAndTypesWithOriginalValues(allTransformations, esi, env.parentExecutionStepInfo);
            return executionStepInfoMapper.mapExecutionStepInfo(esiWithMappedField, environment);
        };
        FetchedValueAnalysis mappedFVA = fetchedValueAnalysisMapper.mapFetchedValueAnalysis(fetchedValueAnalysis, environment,
                esiMapper);
        return new UnapplyResult(subTree.withNewFetchedValueAnalysis(mappedFVA), TraversalControl.CONTINUE);
    }


}
