package org.umn.distributed.server;

import java.util.Collection;

public class ArticleWithDistributionList {
	private Article article;
	private Collection<? extends Machine> distributionCollection;
	
	public ArticleWithDistributionList(Article article,
			Collection<? extends Machine> distributionCollection) {
		this.article = article;
		this.distributionCollection = distributionCollection;
	}

	public Article getArticle() {
		return article;
	}



	public Collection<? extends Machine> getDistributionCollection() {
		return distributionCollection;
	}

	@Override
	public String toString() {
		return "ArticleWithDistributionList [article=" + article
				+ ", distributionCollection=" + distributionCollection + "]";
	}
}