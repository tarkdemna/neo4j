/**
 * Copyright (c) 2002-2015 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.compiler.v2_3.planner.logical

import org.neo4j.cypher.internal.commons.CypherFunSuite
import org.neo4j.cypher.internal.compiler.v2_3.ast.{LabelName, UsingIndexHint}
import org.neo4j.cypher.internal.compiler.v2_3.planner.logical.Metrics.QueryGraphCardinalityInput
import org.neo4j.cypher.internal.compiler.v2_3.planner.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.compiler.v2_3.planner.logical.steps.{LogicalPlanProducer, pickBestPlanUsingHintsAndCost}
import org.neo4j.cypher.internal.compiler.v2_3.planner.{CardinalityEstimation, LogicalPlanningTestSupport2, PlannerQuery}

class PickBestPlanUsingHintsAndCostTest extends CypherFunSuite with LogicalPlanningTestSupport2 {

  val GIVEN_FIXED_COST = new given {
    cost = {
      case _ => Cost(100)
    }
  }

  val hint1: UsingIndexHint = UsingIndexHint(ident("n"), LabelName("Person")_, ident("name"))_
  val hint2: UsingIndexHint = UsingIndexHint(ident("n"), LabelName("Person")_, ident("age"))_
  val hint3: UsingIndexHint = UsingIndexHint(ident("n"), LabelName("Person")_, ident("income"))_

  test("picks the right plan by cost, no matter the cardinality") {
    val a = fakeLogicalPlanFor("a")
    val b = fakeLogicalPlanFor("b")

    assertTopPlan(winner = b, a, b)(new given {
      cost = {
        case p if p == a => Cost(100)
        case p if p == b => Cost(50)
      }
    })
  }

  test("picks the right plan by cost, no matter the size of the covered ids") {
    val ab = fakeLogicalPlanFor("a", "b")
    val b = fakeLogicalPlanFor("b")

    val GIVEN = new given {
      cost = {
        case p if p == ab => Cost(100)
        case p if p == b => Cost(50)
      }
    }

    assertTopPlan(winner = b, ab, b)(GIVEN)
  }

  test("picks the right plan by cost and secondly by the covered ids") {
    val ab = fakeLogicalPlanFor("a", "b")
    val c = fakeLogicalPlanFor("c")

    assertTopPlan(winner = ab, ab, c)(GIVEN_FIXED_COST)
  }

  test("Prefers plans that solves a hint over plan that solves no hint") {
    val f: PlannerQuery => PlannerQuery = (query: PlannerQuery) => query.updateGraph(_.addHints(Some(hint1)))
    val a = fakeLogicalPlanFor("a").updateSolved(f)
    val b = fakeLogicalPlanFor("a")

    assertTopPlan(winner = a, a, b)(GIVEN_FIXED_COST)
  }

  test("Prefers plans that solve more hints") {
    val f: PlannerQuery => PlannerQuery = (query: PlannerQuery) => query.updateGraph(_.addHints(Some(hint1)))
    val a = fakeLogicalPlanFor("a").updateSolved(f)
    val g: PlannerQuery => PlannerQuery = (query: PlannerQuery) => query.updateGraph(_.addHints(Seq(hint1, hint2)))
    val b = fakeLogicalPlanFor("a").updateSolved(g)

    assertTopPlan(winner = b, a, b)(GIVEN_FIXED_COST)
  }

  test("Prefers plans that solve more hints in tails") {
    val f: PlannerQuery => PlannerQuery = (query: PlannerQuery) => query.updateGraph(_.addHints(Some(hint1)))
    val a = fakeLogicalPlanFor("a").updateSolved(f)
    val g: PlannerQuery => PlannerQuery = (query: PlannerQuery) => query.withTail(PlannerQuery.empty.updateGraph(_.addHints(Seq(hint1, hint2))))
    val b = fakeLogicalPlanFor("a").updateSolved(g)

    assertTopPlan(winner = b, a, b)(GIVEN_FIXED_COST)
  }

  private def assertTopPlan(winner: LogicalPlan, candidates: LogicalPlan*)(GIVEN: given) {
    val environment = LogicalPlanningEnvironment(GIVEN)
    val metrics: Metrics = environment.metricsFactory.newMetrics(GIVEN.graphStatistics)
    implicit val context = LogicalPlanningContext(null, LogicalPlanProducer(metrics.cardinality), metrics, null, null, QueryGraphCardinalityInput(Map.empty, Cardinality(1)))
    pickBestPlanUsingHintsAndCost(context)(candidates) should equal(Some(winner))
    pickBestPlanUsingHintsAndCost(context)(candidates.reverse) should equal(Some(winner))
  }

  private implicit def lift(f: PlannerQuery => PlannerQuery): PlannerQuery with CardinalityEstimation => PlannerQuery with CardinalityEstimation =
    (solved: PlannerQuery with CardinalityEstimation) => CardinalityEstimation.lift(f(solved), solved.estimation)
}


