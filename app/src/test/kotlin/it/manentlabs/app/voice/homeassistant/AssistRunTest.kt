// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.homeassistant

import it.manentlabs.app.voice.CommandOutcome
import it.manentlabs.app.voice.homeassistant.HaConversation.Step
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistRunTest {
    private val hello = """{"type":"auth_required","ha_version":"2026.11.1"}"""
    private val ok = """{"type":"auth_ok","ha_version":"2026.11.2"}"""

    private fun event(type: String, data: String = "{}") = """{"id":1,"type":"event","event":{"type":"$type","data":$data}}"""

    private val runStart = event("run-start", """{"runner_data":{"stt_binary_handler_id":7}}""")

    private fun signedIn(pipeline: String? = null) = AssistRun("secret-token", pipeline).also {
        it.onText(hello)
        it.onText(ok)
    }

    private fun intentEnd(response: String) = event("intent-end", """{"intent_output":{"response":$response}}""")

    private fun finished(steps: List<Step<CommandOutcome>>) = (steps.single() as Step.Finish).result

    @Test
    fun `signs in, then asks for a run from speech to intent`() {
        val run = AssistRun("secret-token", "01abc")
        val auth = Json.parseToJsonElement((run.onText(hello).single() as Step.Send).text).jsonObject
        assertEquals("auth", auth["type"]!!.jsonPrimitive.content)
        assertEquals("secret-token", auth["access_token"]!!.jsonPrimitive.content)

        val request = Json.parseToJsonElement((run.onText(ok).single() as Step.Send).text).jsonObject
        assertEquals(1, request["id"]!!.jsonPrimitive.content.toInt())
        assertEquals("assist_pipeline/run", request["type"]!!.jsonPrimitive.content)
        assertEquals("stt", request["start_stage"]!!.jsonPrimitive.content)
        assertEquals("intent", request["end_stage"]!!.jsonPrimitive.content)
        assertEquals(16000, request["input"]!!.jsonObject["sample_rate"]!!.jsonPrimitive.content.toInt())
        assertEquals("01abc", request["pipeline"]!!.jsonPrimitive.content)
        assertFalse("language" in request)
        assertEquals("2026.11.2", run.version)
    }

    @Test
    fun `no pipeline field when none is chosen`() {
        val run = AssistRun("t", " ")
        run.onText(hello)
        val request = Json.parseToJsonElement((run.onText(ok).single() as Step.Send).text).jsonObject
        assertFalse("pipeline" in request)
    }

    @Test
    fun `anything but a greeting first is not Home Assistant`() {
        assertEquals(CommandOutcome.Failed("not Home Assistant"), finished(AssistRun("t", null).onText("""{"type":"hello"}""")))
        assertEquals(CommandOutcome.Failed("not Home Assistant"), finished(AssistRun("t", null).onText("<html>")))
    }

    @Test
    fun `a refused token sends no audio`() {
        val run = AssistRun("t", null)
        run.onText(hello)
        assertEquals(CommandOutcome.Failed("bad token"), finished(run.onText("""{"type":"auth_invalid","message":"Invalid access"}""")))
        val other = AssistRun("t", null).apply { onText(hello) }
        assertEquals(CommandOutcome.Failed("unexpected result"), finished(other.onText("""{"type":"result"}""")))
    }

    @Test
    fun `audio goes once, to the handler run-start names`() {
        val run = signedIn()
        assertTrue(run.onText("""{"id":1,"type":"result","success":true,"result":null}""").isEmpty())
        assertEquals(listOf<Step<CommandOutcome>>(Step.Audio(7)), run.onText(runStart))
        assertTrue(run.onText(runStart).isEmpty())
        assertEquals("run-start", run.stage)
    }

    @Test
    fun `run-start without a handler fails`() {
        assertEquals(CommandOutcome.Failed("no audio handler"), finished(signedIn().onText(event("run-start", "{}"))))
    }

    @Test
    fun `a refused run fails with its code or message`() {
        val refused = """{"id":1,"type":"result","success":false,"error":{"code":"pipeline-not-found","message":"Pipeline not found"}}"""
        assertEquals(CommandOutcome.Failed("pipeline-not-found"), finished(signedIn().onText(refused)))
        val noCode = """{"id":1,"type":"result","success":false,"error":{"message":"Nope"}}"""
        assertEquals(CommandOutcome.Failed("Nope"), finished(signedIn().onText(noCode)))
        assertEquals(CommandOutcome.Failed("run refused"), finished(signedIn().onText("""{"id":1,"type":"result","success":false}""")))
    }

    @Test
    fun `an action done is done, with its confirmation`() {
        val response = """{"response_type":"action_done","speech":{"plain":{"speech":"Turned on the light"}}}"""
        assertEquals(CommandOutcome.Done("Turned on the light", answered = false), finished(signedIn().onText(intentEnd(response))))
    }

    @Test
    fun `a query answer is done and marked as an answer`() {
        val response = """{"response_type":"query_answer","speech":{"plain":{"speech":"The boiler is at 52 degrees"}}}"""
        assertEquals(CommandOutcome.Done("The boiler is at 52 degrees", answered = true), finished(signedIn().onText(intentEnd(response))))
        assertEquals(CommandOutcome.Done(null, answered = true), finished(signedIn().onText(intentEnd("""{"response_type":"query_answer"}"""))))
    }

    @Test
    fun `an error response is not understood`() {
        val coded = """{"response_type":"error","data":{"code":"no_valid_targets"},"speech":{"plain":{"speech":"No device"}}}"""
        assertEquals(CommandOutcome.NotUnderstood("no_valid_targets"), finished(signedIn().onText(intentEnd(coded))))
        val spoken = """{"response_type":"error","speech":{"plain":{"speech":"Sorry, I couldn't understand"}}}"""
        assertEquals(CommandOutcome.NotUnderstood("Sorry, I couldn't understand"), finished(signedIn().onText(intentEnd(spoken))))
        assertEquals(CommandOutcome.NotUnderstood("not understood"), finished(signedIn().onText(intentEnd("""{"response_type":"other"}"""))))
        assertEquals(CommandOutcome.Failed("no response"), finished(signedIn().onText(event("intent-end", "{}"))))
    }

    @Test
    fun `pipeline errors split into not understood and failed`() {
        for (code in listOf("stt-no-text-recognized", "intent-not-supported", "no_intent_match")) {
            assertEquals(CommandOutcome.NotUnderstood(code), finished(signedIn().onText(event("error", """{"code":"$code"}"""))))
        }
        assertEquals(CommandOutcome.Failed("stt-provider-unavailable"), finished(signedIn().onText(event("error", """{"code":"stt-provider-unavailable"}"""))))
        assertEquals(CommandOutcome.Failed("error"), finished(signedIn().onText(event("error"))))
    }

    @Test
    fun `run-end before an intent fails`() {
        assertEquals(CommandOutcome.Failed("ended without an answer"), finished(signedIn().onText(event("run-end"))))
    }

    @Test
    fun `messages for other ids are ignored and arrays are taken apart`() {
        val run = signedIn()
        assertTrue(run.onText("""{"id":2,"type":"event","event":{"type":"run-end"}}""").isEmpty())
        val batch = "[$runStart, ${event("stt-start")}, ${intentEnd("""{"response_type":"action_done"}""")}]"
        val steps = run.onText(batch)
        assertEquals(Step.Audio(7), steps[0])
        assertEquals(CommandOutcome.Done(), (steps[1] as Step.Finish).result)
    }

    @Test
    fun `closing and timing out say how far it got`() {
        assertEquals(CommandOutcome.Failed("unreachable"), AssistRun("t", null).onClosed())
        val run = signedIn()
        assertEquals(CommandOutcome.Failed("closed"), run.onClosed())
        assertEquals(CommandOutcome.Failed("timeout after sign-in"), run.onTimeout())
        run.onText(runStart)
        run.onText(event("stt-end"))
        assertEquals(CommandOutcome.Failed("timeout after stt-end"), run.onTimeout())
    }

    @Test
    fun `audio packets carry the handler and at most 100 ms each`() {
        val pcm = ShortArray(3_500) { (it - 1_000).toShort() }
        val packets = AssistAudio.packets(7, pcm)
        assertEquals(listOf(1 + 3_200, 1 + 3_200, 1 + 600, 1), packets.map { it.size })
        assertTrue(packets.all { it[0] == 7.toByte() })
        // Sample 0 is -1000: 0xFC18, little-endian.
        assertEquals(0x18.toByte(), packets[0][1])
        assertEquals(0xFC.toByte(), packets[0][2])
    }

    @Test
    fun `the connection test lists pipelines`() {
        val test = ConnectionTest("t")
        test.onText(hello)
        val request = Json.parseToJsonElement((test.onText(ok).single() as Step.Send).text).jsonObject
        assertEquals("assist_pipeline/pipeline/list", request["type"]!!.jsonPrimitive.content)
        val reply = """{"id":1,"type":"result","success":true,"result":{"pipelines":[
            {"id":"p1","name":"Home Assistant","language":"en"},{"id":"p2","name":"Casa","language":"it"}],
            "preferred_pipeline":"p2"}}"""
        val check = (test.onText(reply).single() as Step.Finish).result
        assertEquals(
            ConnectionCheck.Ok("2026.11.2", listOf(Pipeline("p1", "Home Assistant", "en"), Pipeline("p2", "Casa", "it")), "p2"),
            check,
        )
    }

    @Test
    fun `the connection test reports problems`() {
        fun signedInTest() = ConnectionTest("t").apply { onText(hello); onText(ok) }
        val noPipelines = """{"id":1,"type":"result","success":true,"result":{"pipelines":[]}}"""
        assertEquals(ConnectionCheck.Unreachable("no pipelines"), (signedInTest().onText(noPipelines).single() as Step.Finish).result)
        val refused = """{"id":1,"type":"result","success":false,"error":{"code":"unknown_command"}}"""
        assertEquals(ConnectionCheck.Unreachable("unknown_command"), (signedInTest().onText(refused).single() as Step.Finish).result)
        val bad = ConnectionTest("t").apply { onText(hello) }
        assertEquals(ConnectionCheck.BadToken, (bad.onText("""{"type":"auth_invalid"}""").single() as Step.Finish).result)
        assertEquals(ConnectionCheck.Unreachable("unreachable"), ConnectionTest("t").onClosed())
        assertEquals(ConnectionCheck.Unreachable("timeout"), signedInTest().onTimeout())
    }
}
