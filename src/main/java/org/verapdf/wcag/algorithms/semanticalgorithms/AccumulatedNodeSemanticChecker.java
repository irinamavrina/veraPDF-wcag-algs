package org.verapdf.wcag.algorithms.semanticalgorithms;

import org.verapdf.wcag.algorithms.entities.IDocument;
import org.verapdf.wcag.algorithms.entities.INode;
import org.verapdf.wcag.algorithms.entities.ITree;
import org.verapdf.wcag.algorithms.entities.tables.Table;
import org.verapdf.wcag.algorithms.entities.tables.TableBordersCollection;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.AccumulatedNodeConsumer;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.ClusterTableConsumer;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.LinesPreprocessingConsumer;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.SemanticDocumentPreprocessingConsumer;

import java.util.function.Consumer;

public class AccumulatedNodeSemanticChecker implements ISemanticsChecker {

	@Override
	public void checkSemanticDocument(IDocument document) {
		ITree tree = document.getTree();

		LinesPreprocessingConsumer linesPreprocessingConsumer = new LinesPreprocessingConsumer(document);
		linesPreprocessingConsumer.findTableBorders();

		Consumer<INode> semanticDocumentValidator = new SemanticDocumentPreprocessingConsumer(document,
				linesPreprocessingConsumer.getLinesCollection());
		tree.forEach(semanticDocumentValidator);

		Table.updateTableCounter();

		Consumer<INode> semanticDetectionValidator = new AccumulatedNodeConsumer();
		tree.forEach(semanticDetectionValidator);

		ClusterTableConsumer tableFinder = new ClusterTableConsumer(new TableBordersCollection(
				linesPreprocessingConsumer.getTableBorders()));
		tree.forEach(tableFinder);
	}
}
