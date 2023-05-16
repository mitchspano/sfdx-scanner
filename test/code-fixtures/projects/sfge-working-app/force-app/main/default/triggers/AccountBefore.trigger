trigger AccountBefore on Account (before insert) {
	TriggerHandler th = new TriggerHandler();
	if (th.doTheThing()) {
		System.debug('wow many items');
	}
}
