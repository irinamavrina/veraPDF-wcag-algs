package org.verapdf.wcag.algorithms.entities;

import org.verapdf.wcag.algorithms.entities.enums.SemanticType;

import java.util.Objects;

public class SemanticParagraph extends SemanticNode {

	private boolean enclosedTop;
	private boolean enclosedBottom;
	private int indentation; // 0 - left, 1 - right, 2 - center
	private SemanticTextChunk firstLine;
	private SemanticTextChunk lastLine;

	public SemanticParagraph() {
	}

	public SemanticParagraph(Integer pageNumber, double[] boundingBox, Integer lastPageNumber, SemanticTextChunk firstLine,
	                         SemanticTextChunk lastLine) {
		super(pageNumber, lastPageNumber, boundingBox, SemanticType.PARAGRAPH);
		this.firstLine = firstLine;
		this.lastLine = lastLine;
	}

	public boolean isEnclosedTop() {
		return enclosedTop;
	}

	public void setEnclosedTop(boolean enclosedTop) {
		this.enclosedTop = enclosedTop;
	}

	public boolean isEnclosedBottom() {
		return enclosedBottom;
	}

	public void setEnclosedBottom(boolean enclosedBottom) {
		this.enclosedBottom = enclosedBottom;
	}

	public SemanticTextChunk getFirstLine() {
		return firstLine;
	}

	public void setFirstLine(SemanticTextChunk firstLine) {
		this.firstLine = firstLine;
	}

	public SemanticTextChunk getLastLine() {
		return lastLine;
	}

	public void setLastLine(SemanticTextChunk lastLine) {
		this.lastLine = lastLine;
	}

	public int getIndentation() {
		return indentation;
	}

	public void setIndentation(int indentation) {
		this.indentation = indentation;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		if (!super.equals(o)) {
			return false;
		}
		SemanticParagraph that = (SemanticParagraph) o;
		return enclosedTop == that.enclosedTop
		       && enclosedBottom == that.enclosedBottom
		       && indentation == that.indentation
		       && Objects.equals(firstLine, that.firstLine)
		       && Objects.equals(lastLine, that.lastLine);
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), enclosedTop, enclosedBottom, indentation, firstLine, lastLine);
	}

	@Override
	public String toString() {
		return "SemanticParagraph{" +
		       "enclosedTop=" + enclosedTop +
		       ", enclosedBottom=" + enclosedBottom +
		       ", indentation=" + indentation +
		       ", firstLine=" + firstLine +
		       ", lastLine=" + lastLine +
		       '}';
	}
}
