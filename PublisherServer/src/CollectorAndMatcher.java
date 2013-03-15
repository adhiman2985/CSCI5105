import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;
import org.umn.distributed.server.Article;
import org.umn.distributed.server.ArticleWithDistributionList;
import org.umn.distributed.server.ClientMachine;
import org.umn.distributed.server.Machine;
import org.umn.distributed.server.MatcherUtils;

public class CollectorAndMatcher {
	private ExecutorService matcherService;
	private ArticleDispatcher artDispatcher;
	private final ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, Set<Machine>>>> clientSubscriptions;
	private Set<ClientMachine> readOnlyAttachedClients;
	private Collection<ServerMachine> readOnlyAttachedServers;
	private Logger logger = Logger.getLogger(this.getClass());
	public void shutDown(){
		this.matcherService.shutdown();
	}
	
	protected CollectorAndMatcher(
			ArticleDispatcher pArtDispatcher,
			Set<ClientMachine> readOnlyAttachedClients,
			Set<ServerMachine> readOnlyAttachedServers,
			ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, Set<Machine>>>> pClientSubscriptions,
			int poolSize) {
		this.artDispatcher = pArtDispatcher;
		this.matcherService = Executors.newFixedThreadPool(poolSize);
		this.clientSubscriptions = pClientSubscriptions; // shared from the
															// PubSub server

		this.readOnlyAttachedClients = readOnlyAttachedClients;
		this.readOnlyAttachedServers = readOnlyAttachedServers;
	}

	protected boolean publishClient(Article a, Machine sentBy) {

		if (a != null) {
			// TODO do we need machineInfo here?
			matcherService.execute(new Matcher(a, sentBy, clientSubscriptions));
			return true;
		}
		return false;
	}

	/**
	 * Instances of this class should be passed to ExecutorService for
	 * processing
	 * 
	 * @author akinra
	 * 
	 */
	private class Matcher implements Runnable {
		private final Article artToMatch;
		private final ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, Set<Machine>>>> clientSubscriptions;
		private final Machine sentBy;

		protected Matcher(
				Article a,
				Machine pSentBy,
				ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, Set<Machine>>>> pClientSubscriptions) {
			this.artToMatch = a;
			this.clientSubscriptions = pClientSubscriptions;
			this.sentBy = pSentBy;
		}

		@Override
		public void run() {
			logger.debug("Processing article:" + this.artToMatch);

			List<Machine> distributionList = null;
			// distributionList = getProxyDistributionList();
			distributionList = getDistributionCollection(this.artToMatch,
					this.clientSubscriptions);
			if (distributionList != null) {
				ArticleWithDistributionList adl = new ArticleWithDistributionList(
						artToMatch, distributionList);
				artDispatcher.add(adl);
			} else {
				logger.debug("***********No one subscribed to article = "
						+ this.artToMatch.getSubFormat());
			}

		}

		private List<Machine> getDistributionCollection(
				Article artToMatch2,
				ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, Set<Machine>>>> clientSubscriptions2) {
			logger.debug("getDistributionCollection = "+clientSubscriptions2);

			Set<Machine> machineClone = getMatchedMachines(
					clientSubscriptions2, artToMatch2);
			List<Machine> lst;
			if (machineClone != null) {
				machineClone.remove(sentBy);
				lst = new LinkedList<Machine>();
				lst.addAll(machineClone);
				return lst;
			}
			return null;

		}

		private Set<Machine> getMatchedMachines(
				ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, Set<Machine>>>> clientSubscriptions2,
				Article artToMatch2) {
			Set<Machine> distributionCollection = null;
			List<ConcurrentHashMap<String, ConcurrentHashMap<String, Set<Machine>>>> firstLevelMatches = MatcherUtils
					.getFirstLevelMatches(artToMatch2, clientSubscriptions2,
							false);
			if (firstLevelMatches.size() > 0) {
				List<ConcurrentHashMap<String, Set<Machine>>> secondLevelMatches = new LinkedList<ConcurrentHashMap<String, Set<Machine>>>();
				for (ConcurrentHashMap<String, ConcurrentHashMap<String, Set<Machine>>> match : firstLevelMatches) {
					if (match != null) {
						secondLevelMatches.addAll(MatcherUtils
								.getSecondLevelMatches(artToMatch2, match,
										false));
					}
				}
				if (secondLevelMatches.size() > 0) {
					Set<Machine> thirdLevelMatches = new HashSet<Machine>();
					for (ConcurrentHashMap<String, Set<Machine>> match2 : secondLevelMatches) {
						if (match2 != null) {
							thirdLevelMatches.addAll(MatcherUtils
									.getThirdLevelMatches(artToMatch2, match2,
											false));
						}
					}
					distributionCollection = thirdLevelMatches;
				}
			}
			return distributionCollection;
		}

	}

	public void pubServers(Article a, Machine senderMachine) {

		if (this.readOnlyAttachedServers != null) {
			ArticleWithDistributionList adl = new ArticleWithDistributionList(
					a, this.readOnlyAttachedServers);
			artDispatcher.add(adl);
		} else {
			 logger.info("***********No attached servers");
		}

	}

}
