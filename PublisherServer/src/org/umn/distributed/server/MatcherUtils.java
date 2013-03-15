package org.umn.distributed.server;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MatcherUtils {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

	public static List<ConcurrentHashMap<String, ConcurrentHashMap<String, Set<Machine>>>> getFirstLevelMatches(
	Article artToMatch2,
	ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, Set<Machine>>>> clientSubscriptions2) {
		return getFirstLevelMatches(artToMatch2, clientSubscriptions2,
				false);
	}

	public static List<ConcurrentHashMap<String, ConcurrentHashMap<String, Set<Machine>>>> getFirstLevelMatches(
			Article artToMatch2,
			ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, Set<Machine>>>> clientSubscriptions2, boolean specific) {
		List<ConcurrentHashMap<String, ConcurrentHashMap<String, Set<Machine>>>> list = new LinkedList<ConcurrentHashMap<String, ConcurrentHashMap<String, Set<Machine>>>>();
		/*for (Map.Entry<String, ConcurrentHashMap<String, ConcurrentHashMap<String, Set<Machine>>>> maps : clientSubscriptions2
				.entrySet()) {
			System.out.println(">>" + maps.getKey() + "<< value>>"
					+ maps.getValue() + "<<");
		}*/
		if (artToMatch2.getArticleType() != null
				&& clientSubscriptions2.containsKey(artToMatch2
						.getArticleType())) {
			// if specific == true then if artType is MatchAll then only MatchAll will be returned.
			list.add(clientSubscriptions2.get(artToMatch2.getArticleType()));
			if (!artToMatch2.getArticleType().equals(Article.MATCH_ALL) && clientSubscriptions2.containsKey(Article.MATCH_ALL)) {
				list.add(clientSubscriptions2.get(Article.MATCH_ALL));
			}
		} else {
			if (!specific) {
				// We do not want returning everything if nothing matched, it could mean that a client never 
				// subscribed to such an article.
				
				if (clientSubscriptions2.containsKey(Article.MATCH_ALL)) {
					// if the articleType does not exists in the collection then we need
					// to return the all match
					list.add(clientSubscriptions2.get(Article.MATCH_ALL));
				}
			}
		}
		return list;
	}

	public static List<ConcurrentHashMap<String, Set<Machine>>> getSecondLevelMatches(
	Article artToMatch2,
	ConcurrentHashMap<String, ConcurrentHashMap<String, Set<Machine>>> match) {
		return getSecondLevelMatches(artToMatch2, match, false);
	}

	public static List<ConcurrentHashMap<String, Set<Machine>>> getSecondLevelMatches(
			Article artToMatch2,
			ConcurrentHashMap<String, ConcurrentHashMap<String, Set<Machine>>> match, boolean specific) {
		List<ConcurrentHashMap<String, Set<Machine>>> list = new LinkedList<ConcurrentHashMap<String, Set<Machine>>>();
		if (artToMatch2.getArticleOrignator() != null
				&& match.containsKey(artToMatch2.getArticleOrignator())) {
			list.add(match.get(artToMatch2.getArticleOrignator()));
			if (!artToMatch2.getArticleOrignator().equals(Article.MATCH_ALL) && match.containsKey(Article.MATCH_ALL)) {
				list.add(match.get(Article.MATCH_ALL));
			}
		} else {
			if (!specific && match.containsKey(Article.MATCH_ALL)) {
				list.add(match.get(Article.MATCH_ALL));
			}
		}
		return list;
	}

	public static Set<? extends Machine> getThirdLevelMatches(
	Article artToMatch2, ConcurrentHashMap<String, Set<Machine>> match2) {
		return getThirdLevelMatches(artToMatch2, match2, false);
	}

	public static Set<? extends Machine> getThirdLevelMatches(
			Article artToMatch2, ConcurrentHashMap<String, Set<Machine>> match2, boolean specific) {
		Set<Machine> allSet = new HashSet<Machine>();
		if (artToMatch2.getArticleOrg() != null
				&& match2.containsKey(artToMatch2.getArticleOrg())) {
			allSet.addAll(match2.get(artToMatch2.getArticleOrg()));
			if (!artToMatch2.getArticleOrg().equals(Article.MATCH_ALL) && match2.containsKey(Article.MATCH_ALL)) {
				allSet.addAll(match2.get(Article.MATCH_ALL));
			}
		} else {
			if (!specific && match2.containsKey(Article.MATCH_ALL)) {
				allSet.addAll(match2.get(Article.MATCH_ALL));
			}
		}
		return allSet;
	}

}
