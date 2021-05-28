package org.verapdf.wcag.algorithms.semanticalgorithms.consumers;

import org.verapdf.wcag.algorithms.entities.*;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.tables.Table;
import org.verapdf.wcag.algorithms.semanticalgorithms.tables.TableRecognitionArea;
import org.verapdf.wcag.algorithms.semanticalgorithms.tables.TableRecognizer;
import org.verapdf.wcag.algorithms.entities.tables.TableToken;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.TextChunkUtils;

import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class ClusterTableConsumer implements Consumer<INode> {

    private static final Logger LOGGER = Logger.getLogger(AccumulatedNodeConsumer.class.getCanonicalName());
    private static final Set<SemanticType> tableSemanticTypes = new HashSet<>(Arrays.asList(
                                                        SemanticType.TABLE, SemanticType.TABLE_ROW,
                                                        SemanticType.TABLE_HEADER, SemanticType.TABLE_CELL));

    private TableRecognitionArea recognitionArea;
    private List<Table> tables;

    private List<INode> tableNodes;
    private INode currentHeaderNodes;
    private INode currentTableBodyNodes;

    public  ClusterTableConsumer() {
        tables = new ArrayList<>();
        tableNodes = new ArrayList<>();

        init();
    }

    private void init() {
        recognitionArea = new TableRecognitionArea();

        currentHeaderNodes = new SemanticGroupingNode();
        currentHeaderNodes.setSemanticType(SemanticType.TABLE_ROW);

        currentTableBodyNodes = new SemanticGroupingNode();
        currentTableBodyNodes.setSemanticType(SemanticType.TABLE_ROW);
    }

    public List<Table> getTables() {
        return tables;
    }

    public List<INode> getTableNodes() {
        return tableNodes;
    }

    @Override
    public void accept(INode node) {

        if (node instanceof SemanticSpan) {

            SemanticSpan span = (SemanticSpan) node;
            for (TextLine line : span.getLines()) {
                for (TextChunk chunk : line.getTextChunks()) {

                    if (TextChunkUtils.isSpaceChunk(chunk)) {
                        continue;
                    }

                    TableToken token = new TableToken(chunk, node);
                    recognitionArea.addTokenToRecognitionArea(token);

                    if (recognitionArea.isComplete()) {
                        if (recognitionArea.isValid()) {
                            recognize();
                        }
                        init();
                        accept(node);
                    } else if (recognitionArea.hasCompleteHeaders()) {
                        currentTableBodyNodes.addChild(node);
                    } else {
                        currentHeaderNodes.addChild(node);
                    }
                }
            }

        }

        if (node.isRoot()) {
            if (recognitionArea.isValid()) {
                recognize();
            }

            updateTreeWithRecognizedTables(node);
        }
    }

    private void recognize() {
        TableRecognizer recognizer = new TableRecognizer(recognitionArea);
        recognizer.recognize();
        Table recognizedTable = recognizer.getTable();

        if (recognizedTable != null) {

            tables.add(recognizedTable);

            INode table = new SemanticGroupingNode();
            table.setSemanticType(SemanticType.TABLE);
            table.addChild(currentHeaderNodes);
            table.addChild(currentTableBodyNodes);
            tableNodes.add(table);
        }
    }

    /**
     * main algorithm complexity: max{ O(t * h), O(N) },
     * where N - number of nodes, h - tree height, t - number of table cells
     * node info initialization: O(T * N), where T - number of tables.
     * The worst case is when all table roots are the same node - tree root
     */
    private void updateTreeWithRecognizedTables(INode root) {
        initTreeNodeInfo(root, false);

        for (INode table : tableNodes) {
            INode tableRoot = null;
            for (INode node : new SemanticTree(table)) {
                if (!node.isLeaf() || node.isRoot()) {
                    continue;
                }

                node = node.getParent();
                node.setSemanticType(SemanticType.TABLE_CELL);
                node.setCorrectSemanticScore(1.0);
                node.getNodeInfo().counter = 1;
                if (tableRoot == null) {
                    tableRoot = node;
                }

                while (!node.isRoot()) {
                    INode parent = node.getParent();
                    NodeInfo parentInfo = parent.getNodeInfo();

                    parentInfo.counter++;
                    if (parentInfo.counter > 1) {
                        if (parentInfo.depth < tableRoot.getNodeInfo().depth) {
                            tableRoot = parent;
                        }
                        break;
                    }
                    node = parent;
                }
            }

            for (INode header : table.getChildren().get(0).getChildren()) {
                if (!header.isRoot()) {
                    header.getParent().setSemanticType(SemanticType.TABLE_HEADER);
                    header.getParent().setCorrectSemanticScore(1.0);
                }
            }

            tableRoot.setSemanticType(SemanticType.TABLE);
            tableRoot.setCorrectSemanticScore(1.0);

            // clean up counters
            initTreeNodeInfo(tableRoot, true);
            while (!tableRoot.isRoot()) {
                tableRoot = tableRoot.getParent();
                tableRoot.getNodeInfo().counter = 0;
            }
        }
    }

    private void initTreeNodeInfo(INode root, boolean setupIntermediateTypes) {
        Stack<INode> nodeStack = new Stack<>();
        nodeStack.push(root);

        while (!nodeStack.isEmpty()) {
            INode node = nodeStack.pop();
            NodeInfo nodeInfo = node.getNodeInfo();

            if (node.isRoot()) {
                nodeInfo.depth = 0;
            } else {
                nodeInfo.depth = node.getParent().getNodeInfo().depth + 1;
            }

            if (nodeInfo.counter != 0) {
                nodeInfo.counter = 0;
                if (setupIntermediateTypes && !node.isLeaf() &&
                    !tableSemanticTypes.contains(node.getSemanticType())) {
                    node.setSemanticType(SemanticType.TABLE_ROW);
                    node.setCorrectSemanticScore(1.0);
                }
            }

            for (INode child : node.getChildren()) {
                nodeStack.push(child);
            }
        }
    }
}
