package graphql.nadel.engine

import graphql.language.AstPrinter
import graphql.nadel.result.ExecutionResultNode
import graphql.nadel.result.LeafExecutionResultNode
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLObjectType
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import graphql.util.TraverserVisitorStub
import graphql.util.TreeTransformerUtil
import spock.lang.Specification

import static graphql.nadel.testutils.ExecutionResultNodeUtil.leaf
import static graphql.nadel.testutils.ExecutionResultNodeUtil.list
import static graphql.nadel.testutils.ExecutionResultNodeUtil.object
import static graphql.nadel.testutils.ExecutionResultNodeUtil.root
import static graphql.nadel.testutils.ExecutionResultNodeUtil.toData
import static graphql.nadel.testutils.TestUtil.mkField

class ArtificialFieldUtilsTest extends Specification {

    def context = NadelContext.newContext().build()
    def underscoreTypeNameAlias = context.underscoreTypeNameAlias
    def interfaceType = GraphQLInterfaceType.newInterface().name("I").build()
    def objectType = GraphQLObjectType.newObject().name("O").build()

    def "test that underscore type alias is added on interfaces"() {

        def petField = mkField('''
            pet {
                name
                title
            }
        ''')

        when:
        def newField = ArtificialFieldUtils.maybeAddUnderscoreTypeName(context, petField, interfaceType)
        then:
        AstPrinter.printAstCompact(newField) == """pet {name title $underscoreTypeNameAlias:__typename}"""
    }

    def "test that underscore type alias is added on interfaces even if they have an __typename"() {

        def petField = mkField('''
            pet {
                name
                title
                ... {
                    __typename
                }
            }
        ''')

        when:
        def newField = ArtificialFieldUtils.maybeAddUnderscoreTypeName(context, petField, interfaceType)
        then:
        AstPrinter.printAstCompact(newField) == """pet {name title ... {__typename} $underscoreTypeNameAlias:__typename}"""
    }

    def "test that underscore type alias is skipped on object"() {

        def petField = mkField('''
            pet {
                name
                title
            }
        ''')

        when:
        def newField = ArtificialFieldUtils.maybeAddUnderscoreTypeName(context, petField, objectType)
        then:
        AstPrinter.printAstCompact(newField) == """pet {name title}"""
    }

    def "test that it removes the aliased field but leaves the specific one alone"() {

        def startingNode = root([
                object("pet", [
                        leaf("__typename"), //  <-- manually added by consumer
                        list("owners", [
                                object("X", [
                                        leaf("__typename"), //  <-- manually added by consumer say via Fragment Spread
                                        leaf("name"),
                                        leaf("__typename", underscoreTypeNameAlias),
                                        leaf("title"),
                                ]),
                                object("Y", [
                                        leaf("name"),
                                        leaf("title"),
                                ]),
                        ])
                ])
        ])

        when:
        def newNode = removeArtificialFields(context, startingNode)
        def data = toData(newNode)
        then:
        data == [
                pet: [
                        __typename: "__typenameVal",
                        owners    : [
                                [
                                        __typename: "__typenameVal",
                                        name      : "nameVal",
                                        title     : "titleVal",
                                ],
                                [
                                        name : "nameVal",
                                        title: "titleVal",
                                ],
                        ]
                ]
        ]
    }

    def "test that leaves everything alone"() {

        def startingNode = root([
                object("pet", [
                        leaf("__typename"), //  <-- manually added by consumer
                        list("owners", [
                                object("X", [
                                        leaf("__typename"), //  <-- manually added by consumer say via Fragment Spread
                                        leaf("name"),
                                        leaf("title"),
                                ]),
                                object("Y", [
                                        leaf("name"),
                                        leaf("title"),
                                ]),
                        ])
                ])
        ])

        when:
        def newNode = removeArtificialFields(context, startingNode)
        then:
        // zippers allow for no change to objects if there is no change
        newNode == startingNode
    }

    static ExecutionResultNode removeArtificialFields(NadelContext nadelContext, ExecutionResultNode resultNode) {
        ResultNodesTransformer resultNodesTransformer = new ResultNodesTransformer();
        ExecutionResultNode newNode = resultNodesTransformer.transform(resultNode, new TraverserVisitorStub<ExecutionResultNode>() {
            @Override
            TraversalControl enter(TraverserContext<ExecutionResultNode> context) {
                ExecutionResultNode node = context.thisNode()
                if (node instanceof LeafExecutionResultNode) {
                    LeafExecutionResultNode leaf = (LeafExecutionResultNode) node;

                    if (ArtificialFieldUtils.isArtificialField(nadelContext, leaf.getAlias())) {
                        return TreeTransformerUtil.deleteNode(context);
                    }
                }
                return TraversalControl.CONTINUE;
            }
        });
        return newNode;
    }


}
