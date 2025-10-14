// Client-side logging of mined steps only
// Using canonical LO types: T, E, A, F, Test

import { LoType } from '../types/api';

interface SessionEvent {
    ts: number;
    sessionId: string;
    courseId: string;
    conceptId: string;
    loType: LoType;
    action: "ENTER" | "COMPLETE";
    timeSpentMs?: number | null;
}

const KEY = "ph:events:v1";

function load(): SessionEvent[] {
    try {
        const raw = localStorage.getItem(KEY);
        return raw ? (JSON.parse(raw) as SessionEvent[]) : [];
    } catch {
        return [];
    }
}

function save(events: SessionEvent[]) {
    localStorage.setItem(KEY, JSON.stringify(events));
}

export function logEvent(evt: SessionEvent) {
    const allowed: LoType[] = ["T", "E", "A", "F", "Test"];
    if (!allowed.includes(evt.loType)) return;
    const events = load();
    events.push(evt);
    save(events);
}
