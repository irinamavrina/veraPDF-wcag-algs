package org.verapdf.wcag.algorithms.semanticalgorithms.consumers;

import org.verapdf.wcag.algorithms.entities.*;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.tables.*;
import org.verapdf.wcag.algorithms.semanticalgorithms.tables.TableRecognitionArea;
import org.verapdf.wcag.algorithms.semanticalgorithms.tables.TableRecognizer;
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
     * main algorithm complexity for each table: max{ O(t * h), O(N) },
     * where N - number of nodes, h - tree height, t - number of table cells
     * node info initialization: O(M), where M - tree size.
     * The worst case is when all table roots are the same node - tree root
     */
    private void updateTreeWithRecognizedTables(INode root) {
        initTreeNodeInfo(root);
        for (Table table : tables) {
            INode tableRoot = updateTreeWithRecognizedTable(table);

            if (tableRoot != null) {
                tableRoot.setSemanticType(SemanticType.TABLE);
                tableRoot.setCorrectSemanticScore(1.0);
            }
        }
    }

    private INode updateTreeWithRecognizedTable(Table table) {
        Map<SemanticType, List<INode>> rowNodes = new HashMap<>();
        rowNodes.put(SemanticType.TABLE_HEADERS, new ArrayList<>());
        rowNodes.put(SemanticType.TABLE_BODY, new ArrayList<>());
        for (TableRow row : table.getRows()) {
            INode rowNode = updateTreeWithRecognizedTableRow(row, table.getId());

            if (rowNode != null) {
                rowNode.setSemanticType(SemanticType.TABLE_ROW);
                rowNode.setCorrectSemanticScore(1.0);

                SemanticType rowType = row.getSemanticType();
                List<INode> nodes = rowNodes.get(rowType);
                if (nodes != null) {
                    nodes.add(rowNode);
                }
            }
        }
        List<INode> localRoots = new ArrayList<>();
        for (Map.Entry<SemanticType, List<INode>> entry : rowNodes.entrySet()) {
            SemanticType type = entry.getKey();
            List<INode> rows = entry.getValue();

            INode localRoot = findLocalRoot(rows);
            if (localRoot != null) {
                if (!isTableNode(localRoot)) {
                    localRoot.setSemanticType(type);
                    localRoot.setCorrectSemanticScore(1.0);
                }
                localRoots.add(localRoot);
            }
        }
        if (localRoots.isEmpty()) {
            return null;
        }
        if (localRoots.size() == 1 || localRoots.get(0) == localRoots.get(1)) {
            return localRoots.get(0);
        }
        if (localRoots.get(0).getNodeInfo().depth < localRoots.get(1).getNodeInfo().depth &&
                isAncestorFor(localRoots.get(0), localRoots.get(1))) {
            return localRoots.get(0);
        } else if (localRoots.get(1).getNodeInfo().depth < localRoots.get(0).getNodeInfo().depth &&
                isAncestorFor(localRoots.get(1), localRoots.get(0))) {
            return localRoots.get(1);
        } else {
            return findLocalRoot(localRoots);
        }
    }

    private INode updateTreeWithRecognizedTableRow(TableRow row, Long id) {
        List<INode> cellNodes = new ArrayList<>();
        for (TableCell cell : row.getCells()) {
            INode cellNode = updateTreeWithRecognizedCell(cell);

            if (cellNode != null) {

                cellNode.setSemanticType(cell.getSemanticType());
                cellNode.setCorrectSemanticScore(1.0);
                cellNode.setRecognizedStructureId(id);

                cellNodes.add(cellNode);
            }
        }

        return findLocalRoot(cellNodes);
    }

    private INode updateTreeWithRecognizedCell(TableCell cell) {
        List<INode> tableLeafNodes = new ArrayList<>();
        for (TableTokenRow tokenRow : cell.getContent()) {
            for (TextChunk chunk : tokenRow.getTextChunks()) {
                if (chunk instanceof TableToken) {
                    TableToken token = (TableToken) chunk;
                    if (token.getNode() != null) {
                        tableLeafNodes.add(token.getNode());
                    }
                }
            }
        }
        return findLocalRoot(tableLeafNodes);
    }

    private INode findLocalRoot(List<INode> nodes) {
        INode localRoot = null;
        for (INode node : nodes) {
            if (!node.isRoot()) {
                node = node.getParent();
            }

            if (localRoot == null) {
                localRoot = node;
            }
            node.getNodeInfo().counter++;

            while (!node.isRoot()) {
                INode parent = node.getParent();
                NodeInfo parentInfo = parent.getNodeInfo();

                parentInfo.counter++;
                if (parentInfo.counter > 1) {
                    if (parentInfo.depth < localRoot.getNodeInfo().depth) {
                        localRoot = parent;
                    }
                    break;
                }
                node = parent;
            }
        }
        initTreeCounters(localRoot);

        return localRoot;
    }

    private boolean isAncestorFor(INode first, INode second) {
        while (!second.isRoot()) {
            second = second.getParent();
            if (second == first) {
                return true;
            }
        }
        return false;
    }

    private boolean isTableNode(INode node) {
        return tableSemanticTypes.contains(node.getSemanticType());
    }

    private void initTreeCounters(INode root) {
        if (root == null) {
            return;
        }
        Stack<INode> nodeStack = new Stack<>();
        nodeStack.push(root);

        while (!nodeStack.isEmpty()) {
            INode node = nodeStack.pop();
            NodeInfo nodeInfo = node.getNodeInfo();

            nodeInfo.counter = 0;
            for (INode child : node.getChildren()) {
                nodeStack.push(child);
            }
        }

        while (!root.isRoot()) {
            root = root.getParent();
            root.getNodeInfo().counter = 0;
        }
    }

    private void initTreeNodeInfo(INode root) {
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
            nodeInfo.counter = 0;

            for (INode child : node.getChildren()) {
                nodeStack.push(child);
            }
        }
    }
}
