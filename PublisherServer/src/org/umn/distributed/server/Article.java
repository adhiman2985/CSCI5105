package org.umn.distributed.server;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author akinra
 * 
 */
public class Article {
	public static String MATCH_ALL = "*";
	private static AtomicInteger id_counter = new AtomicInteger(1);
	private String articleType;
	private String articleOrignator; // TODO is this supposed to be machine
										// where the article originated?
	private String articleOrg;
	private String articleContent;
	private int articleId;

	public Article(String article) {
		if (article == null || article.length() == 0) {
			throw new IllegalArgumentException("Could not parse article="
					+ article);
		}
		String[] article1 = article.split(";", -1);
		System.out.println(Arrays.toString(article1));
		if (article1.length != 4) {
			throw new IllegalArgumentException("Could not parse article="
					+ article + " parsed array=" + Arrays.toString(article1));
		}
		this.articleType = getProperty(article1[0]).toUpperCase();
		this.articleOrignator = getProperty(article1[1]).toUpperCase();
		this.articleOrg = getProperty(article1[2]).toUpperCase();
		this.articleContent = article1[3];
		this.articleId = id_counter.addAndGet(1);
	}

	private String getProperty(String article1) {
		// TODO: Shouldn't consider null here. Null is an error.
		if (article1 == null || article1.trim().length() == 0) {
			return MATCH_ALL;
		}
		return article1;
	}

	@Override
	public String toString() {
		return "Article [articleId=" + articleId + ", articleType="
				+ articleType + ", articleOrignator=" + articleOrignator
				+ ", articleOrg=" + articleOrg + ", articleContent="
				+ articleContent + "]";
	}

	public String getArticleType() {
		return articleType;
	}

	public String getArticleOrignator() {
		return articleOrignator;
	}

	public String getArticleOrg() {
		return articleOrg;
	}

	public String getArticleContent() {
		return articleContent;
	}

	public String getSubFormat() {
		return String.format("%s;%s;%s;", this.articleType.toString(),
				this.articleOrignator.toUpperCase(),
				this.articleOrg.toUpperCase());
	}
	
	public String getPublishFormat() {
		return String.format("%s;%s;%s;%s", this.articleType.toString(),
				this.articleOrignator.toUpperCase(),
				this.articleOrg.toUpperCase(), this.articleContent);
	}

	public int getArticleId() {
		return articleId;
	}
}